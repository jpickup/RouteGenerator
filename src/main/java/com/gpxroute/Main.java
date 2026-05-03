package com.gpxroute;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Main {

    private static final String USAGE =
            "Usage: gpx-route-generator [--library <path>] [--osm-bbox <min_lat,min_lon,max_lat,max_lon>]"
            + " [--osm-filter <type>]... --distance <km> --elevation <m> --output <path>\n"
            + "At least one of --library or --osm-bbox must be provided.\n"
            + "Valid --osm-filter values: highway=footway, highway=path, highway=track,"
            + " highway=bridleway, highway=steps, route=hiking";

    public static void main(String[] args) {
        String libraryPath = null;
        String distanceStr = null;
        String elevationStr = null;
        String outputPath = null;
        String osmBbox = null;
        List<String> osmFilters = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--library"    -> { if (i + 1 < args.length) libraryPath = args[++i]; }
                case "--distance"   -> { if (i + 1 < args.length) distanceStr = args[++i]; }
                case "--elevation"  -> { if (i + 1 < args.length) elevationStr = args[++i]; }
                case "--output"     -> { if (i + 1 < args.length) outputPath = args[++i]; }
                case "--osm-bbox"   -> { if (i + 1 < args.length) osmBbox = args[++i]; }
                case "--osm-filter" -> { if (i + 1 < args.length) osmFilters.add(args[++i]); }
                default -> { /* ignore unknown flags */ }
            }
        }

        // Validate required args
        if (distanceStr == null || elevationStr == null || outputPath == null
                || (libraryPath == null && osmBbox == null)) {
            System.err.println(USAGE);
            System.exit(1);
        }

        // Parse numeric args
        double targetDistance;
        try {
            targetDistance = Double.parseDouble(distanceStr);
        } catch (NumberFormatException e) {
            System.err.println("Invalid value for --distance: '" + distanceStr + "' is not a valid number");
            System.exit(1);
            return;
        }

        double targetElevation;
        try {
            targetElevation = Double.parseDouble(elevationStr);
        } catch (NumberFormatException e) {
            System.err.println("Invalid value for --elevation: '" + elevationStr + "' is not a valid number");
            System.exit(1);
            return;
        }

        List<RouteSegment> segments = new ArrayList<>();
        Set<String> sources = new LinkedHashSet<>();

        // Load GPX library if provided
        if (libraryPath != null) {
            RouteLibrary library = new RouteLibrary();
            Result<List<RouteSegment>> loadResult = library.load(Path.of(libraryPath));
            if (!loadResult.isSuccess()) {
                System.err.println(loadResult.getError());
                System.exit(1);
            }
            segments.addAll(loadResult.getValue());
            sources.add("GPX library");
        }

        // Load OSM segments if bbox provided
        if (osmBbox != null) {
            // Parse and validate bbox
            BoundingBox bbox;
            try {
                String[] parts = osmBbox.split(",");
                if (parts.length != 4) {
                    System.err.println("Invalid --osm-bbox value '" + osmBbox
                            + "': expected min_lat,min_lon,max_lat,max_lon");
                    System.exit(1);
                    return;
                }
                double minLat = Double.parseDouble(parts[0].trim());
                double minLon = Double.parseDouble(parts[1].trim());
                double maxLat = Double.parseDouble(parts[2].trim());
                double maxLon = Double.parseDouble(parts[3].trim());
                bbox = new BoundingBox(minLat, minLon, maxLat, maxLon);
            } catch (NumberFormatException e) {
                System.err.println("Invalid --osm-bbox value '" + osmBbox
                        + "': expected min_lat,min_lon,max_lat,max_lon");
                System.exit(1);
                return;
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid --osm-bbox: " + e.getMessage());
                System.exit(1);
                return;
            }

            // Parse and validate filters
            List<OsmSegmentFilter> filters = new ArrayList<>();
            if (osmFilters.isEmpty()) {
                filters = OsmSegmentFilter.defaults();
            } else {
                for (String filterStr : osmFilters) {
                    Result<OsmSegmentFilter> filterResult = OsmSegmentFilter.fromCliString(filterStr);
                    if (!filterResult.isSuccess()) {
                        System.err.println(filterResult.getError());
                        System.exit(1);
                        return;
                    }
                    filters.add(filterResult.getValue());
                }
            }

            // Fetch OSM segments
            OsmFetcher fetcher = new OsmFetcher();
            Result<List<RouteSegment>> osmResult = fetcher.fetch(bbox, filters);
            if (!osmResult.isSuccess()) {
                System.err.println(osmResult.getError());
                System.exit(1);
            }
            List<RouteSegment> osmSegments = osmResult.getValue();
            System.out.println("Fetched " + osmSegments.size() + " OSM segments.");
            segments.addAll(osmSegments);
            sources.add("OpenStreetMap");
        }

        if (segments.isEmpty()) {
            System.err.println("No segments available. Provide --library and/or --osm-bbox.");
            System.exit(1);
        }

        // Generate route
        RouteGenerator generator = new RouteGenerator();
        Result<Route> generateResult = generator.generate(segments, targetDistance, targetElevation);
        if (!generateResult.isSuccess()) {
            System.err.println(generateResult.getError());
            System.exit(1);
        }
        Route route = generateResult.getValue();

        // Compute metrics
        RouteMetrics.Metrics metrics = RouteMetrics.compute(route);

        // Write output
        GpxPrettyPrinter printer = new GpxPrettyPrinter();
        Result<Path> writeResult = printer.write(route, metrics, sources, Path.of(outputPath));
        if (!writeResult.isSuccess()) {
            System.err.println(writeResult.getError());
            System.exit(1);
        }

        // Success output
        System.out.println("Route generated successfully.");
        System.out.println("Output: " + outputPath);
        System.out.printf("Distance: %.2f km%n", metrics.distanceKm());
        System.out.printf("Elevation Gain: %.0f m%n", metrics.elevationGainM());
    }
}
