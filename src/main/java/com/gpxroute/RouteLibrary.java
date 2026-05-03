package com.gpxroute;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads and indexes all GPX files from a directory.
 *
 * <p>Files that fail to parse are logged as warnings to stderr and skipped.
 * Returns a {@link Result.Failure} if the directory is unreadable, empty,
 * or contains no valid GPX files.
 */
public class RouteLibrary {

    private final GpxParser parser;

    public RouteLibrary() {
        this.parser = new GpxParser();
    }

    /**
     * Loads all GPX files from the given directory.
     *
     * @param libraryPath path to the directory containing GPX files
     * @return {@code Success} with a list of {@link RouteSegment}s, or {@code Failure} if the
     *         directory is unreadable, empty, or contains no valid GPX files
     */
    public Result<List<RouteSegment>> load(Path libraryPath) {
        // 1. Check directory exists and is readable
        if (!Files.exists(libraryPath) || !Files.isDirectory(libraryPath) || !Files.isReadable(libraryPath)) {
            return Result.failure("Library directory is not accessible: " + libraryPath);
        }

        // 2. List all *.gpx files (non-recursive, case-insensitive extension match)
        List<Path> gpxFiles;
        try (Stream<Path> stream = Files.list(libraryPath)) {
            gpxFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".gpx"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return Result.failure("Failed to list library directory '" + libraryPath + "': " + e.getMessage());
        }

        // 3. If no .gpx files found, return failure
        if (gpxFiles.isEmpty()) {
            return Result.failure("No valid GPX files found in library directory: " + libraryPath);
        }

        // 4. Parse each file, skipping failures with a warning
        List<RouteSegment> segments = new ArrayList<>();
        for (Path gpxFile : gpxFiles) {
            Result<Route> parseResult = parser.parse(gpxFile);
            if (!parseResult.isSuccess()) {
                System.err.println("WARNING: Skipping '" + gpxFile + "': " + parseResult.getError());
                continue;
            }
            Route route = parseResult.getValue();
            RouteMetrics.Metrics metrics = RouteMetrics.compute(route);
            segments.add(new RouteSegment(route, metrics.distanceKm(), metrics.elevationGainM()));
        }

        // 5. If no segments were successfully loaded, return failure
        if (segments.isEmpty()) {
            return Result.failure("No valid GPX files found in library directory: " + libraryPath);
        }

        // 6. Return the loaded segments
        return Result.success(segments);
    }
}
