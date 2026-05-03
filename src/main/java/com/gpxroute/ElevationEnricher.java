package com.gpxroute;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Enriches waypoints (which have lat/lon but ele=0.0) with real altitude values
 * from the Open-Elevation API.
 */
public class ElevationEnricher {

    public static final String DEFAULT_ELEVATION_URL = "https://api.open-elevation.com/api/v1/lookup";
    public static final int BATCH_SIZE = 100;
    public static final Duration ELEVATION_TIMEOUT = Duration.ofSeconds(30);

    private final String elevationUrl;
    private final HttpClient httpClient;

    public static ElevationEnricher create() {
        return new ElevationEnricher(DEFAULT_ELEVATION_URL);
    }

    public ElevationEnricher(String elevationUrl) {
        this.elevationUrl = elevationUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(ELEVATION_TIMEOUT)
                .build();
    }

    /**
     * Enriches a list of waypoints with real altitude values.
     * Waypoints are split into batches of at most BATCH_SIZE.
     */
    public Result<List<Waypoint>> enrich(List<Waypoint> waypoints) {
        if (waypoints.isEmpty()) {
            return Result.success(List.of());
        }

        List<Double> allElevations = new ArrayList<>();

        // Split into batches
        for (int i = 0; i < waypoints.size(); i += BATCH_SIZE) {
            List<Waypoint> batch = waypoints.subList(i, Math.min(i + BATCH_SIZE, waypoints.size()));
            String requestBody = buildRequestBody(batch);

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(elevationUrl))
                        .timeout(ELEVATION_TIMEOUT)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    return Result.failure("Open-Elevation API request failed with HTTP " + response.statusCode());
                }

                Result<List<Double>> parsed = parseResponse(response.body());
                if (!parsed.isSuccess()) {
                    return Result.failure(parsed.getError());
                }
                allElevations.addAll(parsed.getValue());

            } catch (java.net.http.HttpTimeoutException e) {
                return Result.failure("Open-Elevation API request timed out after " + ELEVATION_TIMEOUT.getSeconds() + " seconds");
            } catch (Exception e) {
                return Result.failure("Open-Elevation API request failed: " + e.getMessage());
            }
        }

        if (allElevations.size() != waypoints.size()) {
            return Result.failure("Open-Elevation API returned " + allElevations.size()
                    + " elevations for " + waypoints.size() + " waypoints");
        }

        // Reconstruct waypoints with elevations
        List<Waypoint> enriched = new ArrayList<>(waypoints.size());
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint wp = waypoints.get(i);
            enriched.add(new Waypoint(wp.lat(), wp.lon(), allElevations.get(i)));
        }
        return Result.success(enriched);
    }

    /** Builds the Open-Elevation POST request body JSON. */
    String buildRequestBody(List<Waypoint> batch) {
        StringBuilder sb = new StringBuilder("{\"locations\":[");
        for (int i = 0; i < batch.size(); i++) {
            if (i > 0) sb.append(",");
            Waypoint wp = batch.get(i);
            sb.append(String.format("{\"latitude\":%.6f,\"longitude\":%.6f}", wp.lat(), wp.lon()));
        }
        sb.append("]}");
        return sb.toString();
    }

    /** Parses the Open-Elevation JSON response and extracts elevation values in order. */
    Result<List<Double>> parseResponse(String responseBody) {
        try (JsonReader reader = Json.createReader(new StringReader(responseBody))) {
            JsonObject root = reader.readObject();
            JsonArray results = root.getJsonArray("results");
            if (results == null) {
                return Result.failure("Open-Elevation API response missing 'results' field");
            }
            List<Double> elevations = new ArrayList<>(results.size());
            for (int i = 0; i < results.size(); i++) {
                JsonObject result = results.getJsonObject(i);
                if (result.isNull("elevation") || !result.containsKey("elevation")) {
                    double lat = result.getJsonNumber("latitude").doubleValue();
                    double lon = result.getJsonNumber("longitude").doubleValue();
                    return Result.failure(String.format(
                            "Open-Elevation API returned null elevation for coordinate (%.6f, %.6f)", lat, lon));
                }
                elevations.add(result.getJsonNumber("elevation").doubleValue());
            }
            return Result.success(elevations);
        } catch (Exception e) {
            return Result.failure("Failed to parse Open-Elevation API response: " + e.getMessage());
        }
    }
}
