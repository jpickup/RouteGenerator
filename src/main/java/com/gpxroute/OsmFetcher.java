package com.gpxroute;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches OSM ways from the Overpass API and converts them into RouteSegments.
 * Elevation data is attached via ElevationEnricher.
 */
public class OsmFetcher {

    public static final String DEFAULT_OVERPASS_URL = "https://overpass-api.de/api/interpreter";
    public static final Duration OVERPASS_TIMEOUT = Duration.ofSeconds(60);

    private final String overpassUrl;
    private final HttpClient httpClient;
    private final ElevationEnricher elevationEnricher;

    public OsmFetcher() {
        this(DEFAULT_OVERPASS_URL, ElevationEnricher.create());
    }

    public OsmFetcher(String overpassUrl, ElevationEnricher elevationEnricher) {
        this.overpassUrl = overpassUrl;
        this.elevationEnricher = elevationEnricher;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(OVERPASS_TIMEOUT)
                .build();
    }

    /**
     * Fetches OSM ways matching the given filters within the bounding box,
     * enriches them with elevation data, and returns them as RouteSegments.
     */
    public Result<List<RouteSegment>> fetch(BoundingBox bbox, List<OsmSegmentFilter> filters) {
        String query = buildQuery(bbox, filters);

        String responseBody;
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(overpassUrl))
                    .timeout(OVERPASS_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("data=" + encodedQuery))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return Result.failure("Overpass API request failed with HTTP " + response.statusCode());
            }
            responseBody = response.body();

        } catch (java.net.http.HttpTimeoutException e) {
            return Result.failure("Overpass API request timed out after " + OVERPASS_TIMEOUT.getSeconds() + " seconds");
        } catch (Exception e) {
            return Result.failure("Overpass API request failed: " + e.getMessage());
        }

        // Parse response — collect all way waypoint lists first, then do a single bulk elevation call
        // This minimises the number of Open-Elevation API requests (one call per 100 coords total,
        // not one call per way) to avoid hitting rate limits.
        List<List<Waypoint>> wayWaypoints = new ArrayList<>();
        try (JsonReader reader = Json.createReader(new StringReader(responseBody))) {
            JsonObject root = reader.readObject();
            JsonArray elements = root.getJsonArray("elements");
            if (elements == null) {
                return Result.failure("Failed to parse Overpass API response: missing 'elements' field");
            }

            // Build node map: id -> Waypoint (lat/lon only, ele=0.0)
            Map<Long, Waypoint> nodeMap = new HashMap<>();
            for (int i = 0; i < elements.size(); i++) {
                JsonObject el = elements.getJsonObject(i);
                if ("node".equals(el.getString("type", ""))) {
                    long id = el.getJsonNumber("id").longValue();
                    double lat = el.getJsonNumber("lat").doubleValue();
                    double lon = el.getJsonNumber("lon").doubleValue();
                    nodeMap.put(id, new Waypoint(lat, lon, 0.0));
                }
            }

            // Resolve each way's node IDs to waypoints (no elevation yet)
            for (int i = 0; i < elements.size(); i++) {
                JsonObject el = elements.getJsonObject(i);
                if (!"way".equals(el.getString("type", ""))) continue;

                JsonArray nodeIds = el.getJsonArray("nodes");
                if (nodeIds == null || nodeIds.size() < 2) continue;

                List<Waypoint> rawWaypoints = new ArrayList<>(nodeIds.size());
                boolean allResolved = true;
                for (int j = 0; j < nodeIds.size(); j++) {
                    long nodeId = nodeIds.getJsonNumber(j).longValue();
                    Waypoint wp = nodeMap.get(nodeId);
                    if (wp == null) { allResolved = false; break; }
                    rawWaypoints.add(wp);
                }
                if (allResolved) {
                    wayWaypoints.add(rawWaypoints);
                }
            }

        } catch (Exception e) {
            return Result.failure("Failed to parse Overpass API response: " + e.getMessage());
        }

        if (wayWaypoints.isEmpty()) {
            String filterStr = filters.stream()
                    .map(OsmSegmentFilter::toCliString)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("(none)");
            return Result.failure("No OSM ways found in bounding box " + bbox.toOverpassString()
                    + " matching filters: " + filterStr);
        }

        // Build a single flat list of all waypoints across all ways for one bulk elevation call.
        // Track the start index of each way's waypoints within the flat list.
        List<Waypoint> allWaypoints = new ArrayList<>();
        int[] wayStartIndex = new int[wayWaypoints.size()];
        for (int i = 0; i < wayWaypoints.size(); i++) {
            wayStartIndex[i] = allWaypoints.size();
            allWaypoints.addAll(wayWaypoints.get(i));
        }

        // Single bulk elevation enrichment — ElevationEnricher handles internal batching at 100 coords
        Result<List<Waypoint>> enrichedAll = elevationEnricher.enrich(allWaypoints);
        if (!enrichedAll.isSuccess()) {
            return Result.failure("Elevation enrichment failed: " + enrichedAll.getError());
        }
        List<Waypoint> enrichedWaypoints = enrichedAll.getValue();

        // Redistribute enriched waypoints back to each way and build RouteSegments
        List<RouteSegment> segments = new ArrayList<>();
        for (int i = 0; i < wayWaypoints.size(); i++) {
            int start = wayStartIndex[i];
            int end = start + wayWaypoints.get(i).size();
            List<Waypoint> wayEnriched = enrichedWaypoints.subList(start, end);
            Route route = new Route(wayEnriched);
            RouteMetrics.Metrics metrics = RouteMetrics.compute(route);
            segments.add(new RouteSegment(route, metrics.distanceKm(), metrics.elevationGainM()));
        }

        return Result.success(segments);
    }

    /** Builds the Overpass QL query string for the given bbox and filters. */
    String buildQuery(BoundingBox bbox, List<OsmSegmentFilter> filters) {
        String bboxStr = bbox.toOverpassString();
        StringBuilder sb = new StringBuilder();
        sb.append("[out:json][timeout:60];\n(\n");
        for (OsmSegmentFilter filter : filters) {
            sb.append("  way").append(filter.toOverpassTag())
              .append("(").append(bboxStr).append(");\n");
        }
        sb.append(");\nout body;\n>;\nout skel qt;\n");
        return sb.toString();
    }
}
