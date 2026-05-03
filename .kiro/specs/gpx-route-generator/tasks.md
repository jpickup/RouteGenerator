# Implementation Plan: GPX Route Generator

## Overview

Implement a Java command-line application that synthesizes trail running routes by composing segments from a library of existing GPX files. The implementation follows the four-component architecture defined in the design: `GpxParser`, `RouteMetrics`, `RouteGenerator`, and `GpxPrettyPrinter`, orchestrated by a `Main` CLI entry point.

## Tasks

- [x] 1. Set up project structure and domain model
  - Create Maven `pom.xml` with JUnit 5 and jqwik 1.9.3 as test-only dependencies; no external production dependencies
  - Create package structure (e.g., `com.gpxroute` or similar)
  - Implement `Waypoint` record with `lat`, `lon`, `ele` fields
  - Implement `Route` record with `List<Waypoint> waypoints`, defensive copy in compact constructor, and `isEmpty()`, `size()`, `first()`, `last()` helpers
  - Implement `RouteSegment` record with `route`, `distanceKm`, `elevationGainM` fields
  - Implement `Result<T>` sealed interface with `Success<T>` and `Failure<T>` permits, plus `success()`, `failure()`, `isSuccess()`, `getValue()`, `getError()` helpers
  - _Requirements: 1.1, 4.1, 4.2_

- [ ] 2. Implement RouteMetrics
  - [x] 2.1 Implement `RouteMetrics` class with static methods
    - Implement `haversineKm(Waypoint a, Waypoint b)` using the standard Haversine formula with `EARTH_RADIUS_KM = 6371.0`
    - Implement `distanceKm(Route route)` summing Haversine distances between consecutive waypoints
    - Implement `elevationGainM(Route route)` summing positive altitude differences above the 5m smoothing threshold
    - Implement `elevationLossM(Route route)` summing negative altitude differences (as positive value) above the 5m smoothing threshold
    - Implement `isWithinMeters(Waypoint a, Waypoint b, double thresholdMeters)` using Haversine
    - Implement `compute(Route route)` returning a `Metrics` record with all three values
    - Define constants: `ELEVATION_SMOOTHING_THRESHOLD_M = 5.0`, `EARTH_RADIUS_KM = 6371.0`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 5.1_

  - [ ]* 2.2 Write property test for distance non-negativity and single-waypoint zero distance
    - **Property 4: Distance is non-negative and zero for single-waypoint routes**
    - **Validates: Requirements 3.1**
    - Use `@ForAll("validRoutes")` and `@ForAll("validWaypoints")` arbitraries
    - Tag: `// Feature: gpx-route-generator, Property 4: Distance is non-negative and zero for single-waypoint routes`

  - [ ]* 2.3 Write property test for elevation gain and loss non-negativity
    - **Property 5: Elevation gain and loss are non-negative**
    - **Validates: Requirements 3.2, 3.3**
    - Use `@ForAll("validRoutes")` arbitrary
    - Tag: `// Feature: gpx-route-generator, Property 5: Elevation gain and loss are non-negative`

  - [ ]* 2.4 Write property test for sub-threshold elevation filtering
    - **Property 6: Sub-threshold elevation differences are ignored**
    - **Validates: Requirements 3.4**
    - Generate routes where all consecutive altitude differences are strictly less than 5.0m
    - Tag: `// Feature: gpx-route-generator, Property 6: Sub-threshold elevation differences are ignored`

  - [ ]* 2.5 Write property test for proximity check matching Haversine
    - **Property 7: Proximity check matches Haversine distance**
    - **Validates: Requirements 5.1**
    - Assert `isWithinMeters(a, b, 50.0)` iff `haversineKm(a, b) * 1000 ≤ 50.0`
    - Tag: `// Feature: gpx-route-generator, Property 7: Proximity check matches Haversine distance`

  - [ ]* 2.6 Write unit tests for RouteMetrics
    - Test distance between two known coordinates (e.g., London to Paris) within 1% tolerance
    - Test distance of a single-waypoint route returns 0.0
    - Test elevation gain with all differences below 5m threshold returns 0.0
    - Test elevation gain with a mix of gains and losses counts only gains above threshold
    - Test elevation loss with a mix counts only losses above threshold
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

- [ ] 3. Implement GpxParser
  - [x] 3.1 Implement `GpxParser` class using StAX (`XMLInputFactory`)
    - Stream through XML collecting all `<trkpt>` elements across all `<trk>` and `<trkseg>` elements, merging into a single ordered list
    - For each `<trkpt>`, read `lat` and `lon` attributes and the `<ele>` child element
    - Return `Failure` if XML is malformed, if any `<trkpt>` is missing `<ele>`, or if no waypoints are found
    - Implement `parse(Path path)` and `parseString(String gpxXml)` methods
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

  - [ ]* 3.2 Write property test for GPX round-trip waypoint preservation
    - **Property 1: GPX round-trip preserves all waypoints**
    - **Validates: Requirements 1.1, 1.5, 1.6**
    - Requires `GpxPrettyPrinter` stub or implement after task 4; integrate fully once printer is complete
    - Tag: `// Feature: gpx-route-generator, Property 1: GPX round-trip preserves all waypoints`

  - [ ]* 3.3 Write property test for multi-track GPX merge ordering
    - **Property 2: Multi-track GPX merges into a single ordered sequence**
    - **Validates: Requirements 1.2**
    - Build multi-track GPX strings from `List<List<Waypoint>>` and verify ordered concatenation
    - Tag: `// Feature: gpx-route-generator, Property 2: Multi-track GPX merges into a single ordered sequence`

  - [ ]* 3.4 Write property test for missing altitude rejection
    - **Property 3: Parser rejects GPX with missing altitude data**
    - **Validates: Requirements 1.4**
    - Generate GPX strings with at least one `<trkpt>` missing `<ele>` and assert `Failure`
    - Tag: `// Feature: gpx-route-generator, Property 3: Parser rejects GPX with missing altitude data`

  - [ ]* 3.5 Write unit tests for GpxParser
    - Parse a minimal valid GPX file with a single track and single segment
    - Parse a GPX file with multiple `<trk>` elements — verify waypoints are merged in order
    - Parse a GPX file with multiple `<trkseg>` elements within one `<trk>` — verify merge
    - Parse a GPX file with a missing `<ele>` element — verify `Failure` is returned
    - Parse malformed XML — verify `Failure` with a descriptive message
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 4. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 5. Implement GpxPrettyPrinter
  - [x] 5.1 Implement `GpxPrettyPrinter` class using StAX (`XMLOutputFactory`)
    - Write GPX 1.1 XML with `<?xml version="1.0" encoding="UTF-8"?>` declaration
    - Include `<metadata><desc>Distance: X.XX km, Elevation Gain: Y m</desc></metadata>`
    - Write all waypoints as `<trkpt lat="..." lon="..."><ele>...</ele></trkpt>` inside a single `<trk><trkseg>`
    - Implement `write(Route route, RouteMetrics.Metrics metrics, Path outputPath)` returning `Result<Path>`
    - Implement `toGpxString(Route route, RouteMetrics.Metrics metrics)` for testing
    - Return `Failure` with descriptive message if the output path is not writable
    - _Requirements: 1.5, 6.1, 6.2, 6.3, 6.4_

  - [ ]* 5.2 Write property test for GPX output containing distance and elevation metadata
    - **Property 9: GPX output contains distance and elevation metadata**
    - **Validates: Requirements 6.3**
    - Assert `toGpxString()` output contains formatted distance and elevation gain values in `<desc>`
    - Tag: `// Feature: gpx-route-generator, Property 9: GPX output contains distance and elevation metadata`

  - [ ]* 5.3 Complete property test for GPX round-trip (Property 1)
    - Wire `GpxPrettyPrinter` and `GpxParser` together to fully implement the round-trip property test from task 3.2
    - **Property 1: GPX round-trip preserves all waypoints**
    - **Validates: Requirements 1.1, 1.5, 1.6**

  - [ ]* 5.4 Write unit tests for GpxPrettyPrinter
    - Serialize a route and verify the output is parseable XML
    - Verify `<desc>` contains the expected distance and elevation values
    - Verify all waypoints appear in the output with correct lat/lon/ele
    - _Requirements: 1.5, 6.2, 6.3_

- [ ] 6. Implement RouteLibrary
  - [x] 6.1 Implement `RouteLibrary` class
    - List all `*.gpx` files in the given directory (non-recursive)
    - For each file, call `GpxParser.parse()`; on failure log a warning to stderr in the format `WARNING: Skipping '<path>': <error message>` and continue
    - For each successfully parsed route, call `RouteMetrics.compute()` and create a `RouteSegment`
    - Return `Failure` if the directory is empty, unreadable, or no valid GPX files were loaded
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

  - [ ]* 6.2 Write unit tests for RouteLibrary
    - Load a directory of test GPX files and verify all are parsed correctly
    - Load a directory with one invalid file and verify valid ones are loaded with a warning printed to stderr
    - Load an empty directory and verify `Failure` is returned
    - _Requirements: 2.1, 2.2, 2.3_

- [ ] 7. Implement RouteGenerator
  - [x] 7.1 Implement `RouteGenerator` class with backtracking search
    - Define constants: `DISTANCE_TOLERANCE = 0.10`, `ELEVATION_TOLERANCE = 0.15`, `SEGMENT_JOIN_THRESHOLD_M = 50.0`
    - Implement `generate(List<RouteSegment> segments, double targetDistanceKm, double targetElevationGainM)` returning `Result<Route>`
    - Shuffle segments at the start for variety across runs
    - Implement depth-first backtracking `search()` as described in the design
    - Only join segments where the last waypoint of the previous segment is within 50m of the first waypoint of the next segment (using `RouteMetrics.isWithinMeters`)
    - Do not reuse the same segment more than once per route
    - When concatenating segments, remove duplicate consecutive waypoints at join points
    - Track the closest achievable values during search; on failure return a descriptive message with closest distance, elevation, and shortfall
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 5.1, 5.2, 5.3_

  - [ ]* 7.2 Write property test for no duplicate consecutive waypoints in generated routes
    - **Property 8: Generated route has no duplicate consecutive waypoints**
    - **Validates: Requirements 4.5**
    - Use `Assume.that(result.isSuccess())` to skip unsatisfiable inputs
    - Tag: `// Feature: gpx-route-generator, Property 8: Generated route has no duplicate consecutive waypoints`

  - [ ]* 7.3 Write unit tests for RouteGenerator
    - Request with parameters impossible to satisfy — verify `Failure` with closest achievable values in the message
    - Request where only one segment satisfies the constraints — verify that segment is returned
    - Verify no segment appears twice in the output route
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 5.3_

- [x] 8. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 9. Implement Main CLI entry point
  - [x] 9.1 Implement `Main` class with hand-written argument parser
    - Parse `--library`, `--distance`, `--elevation`, `--output` arguments
    - Print a usage message listing all required arguments and exit with code 1 if any required argument is missing
    - Print a descriptive error and exit with code 1 if `--distance` or `--elevation` are not valid numbers
    - Orchestrate the full pipeline: load library → generate route → write GPX → print summary
    - On success, print to stdout: output file path, actual distance (km), actual elevation gain (m)
    - On any failure, print a descriptive error to stderr and exit with code 1
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [ ]* 9.2 Write property test for CLI rejecting non-numeric arguments
    - **Property 10: CLI rejects non-numeric values for numeric arguments**
    - **Validates: Requirements 7.3**
    - Generate arbitrary non-numeric strings for `--distance` and `--elevation` and assert non-zero exit code
    - Tag: `// Feature: gpx-route-generator, Property 10: CLI rejects non-numeric values for numeric arguments`

  - [ ]* 9.3 Write unit tests for Main CLI argument parsing
    - Missing `--library` argument — verify non-zero exit and usage message
    - Missing `--distance` argument — verify non-zero exit and usage message
    - Non-numeric `--distance` value — verify non-zero exit and descriptive error message
    - _Requirements: 7.1, 7.2, 7.3_

- [ ] 10. Integration tests and end-to-end wiring
  - [x] 10.1 Write integration tests using real GPX files on disk
    - Load a directory of test GPX files and verify all are parsed correctly
    - Load a directory with one invalid file and verify valid ones are loaded with a warning
    - Run the full pipeline (load library → generate → write) and verify the output file is a valid GPX that can be re-parsed
    - Validate the output GPX against the GPX 1.1 XSD schema using `javax.xml.validation`
    - Verify the generated route's distance and elevation are within the specified tolerances (±10% distance, ±15% elevation)
    - _Requirements: 4.1, 4.2, 6.1, 6.2_

  - [x] 10.2 Create test GPX fixture files
    - Create a set of small GPX fixture files for use in integration and unit tests
    - Include at least one valid multi-track file, one file with missing altitude, and one malformed XML file
    - Ensure fixture routes are geographically adjacent (within 50m) so the generator can compose them
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 5.1_

- [x] 11. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at key milestones
- Property tests (jqwik 1.9.3) validate universal correctness properties across randomly generated inputs
- Unit tests validate specific examples and edge cases
- The `Result<T>` sealed interface is used throughout — no checked exceptions cross component boundaries
- Production code uses only the Java standard library; jqwik and JUnit 5 are test-only dependencies

---

## OSM Route Segments — Implementation Tasks

- [ ] 12. Add OSM domain model and update pom.xml
  - Add `jakarta.json-api` 2.1.3 and `parsson` 1.1.6 as production-scope dependencies in `pom.xml`
  - Implement `BoundingBox` record with validation in compact constructor
  - Implement `OsmSegmentFilter` enum with `FOOTWAY`, `PATH`, `TRACK`, `BRIDLEWAY`, `STEPS`, `HIKING` values
  - Implement `toCliString()`, `toOverpassTag()`, `fromCliString(String)`, and `defaults()` methods
  - _Requirements: 9.1, 9.5, 12.3, 12.4, 12.5_

- [ ] 13. Implement ElevationEnricher
  - [ ] 13.1 Implement `ElevationEnricher` class using `java.net.http.HttpClient`
    - Implement `enrich(List<Waypoint> waypoints)` returning `Result<List<Waypoint>>`
    - Split waypoints into batches of at most 100 and make sequential POST requests
    - Implement `buildRequestBody(List<Waypoint> batch)` producing Open-Elevation JSON
    - Implement `parseResponse(String responseBody)` extracting elevation values in order
    - Return `Failure` on HTTP error, timeout, or null/absent elevation value
    - Default endpoint: `https://api.open-elevation.com/api/v1/lookup`
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_

  - [ ]* 13.2 Write unit tests for ElevationEnricher
    - `buildRequestBody()` produces valid JSON for a list of waypoints
    - `parseResponse()` extracts elevation values in the correct order
    - A response with a null elevation value returns `Failure`
    - A list of 150 waypoints is split into two batches and results are merged in order
    - _Requirements: 10.1, 10.2, 10.4, 10.6_

- [ ] 14. Implement OsmFetcher
  - [ ] 14.1 Implement `OsmFetcher` class using `java.net.http.HttpClient` and `jakarta.json`
    - Implement `fetch(BoundingBox bbox, List<OsmSegmentFilter> filters)` returning `Result<List<RouteSegment>>`
    - Implement `buildQuery(BoundingBox bbox, List<OsmSegmentFilter> filters)` producing Overpass QL
    - Parse Overpass JSON response: build node map, resolve ways to ordered waypoint lists
    - Skip ways with fewer than 2 nodes
    - For each valid way, call `ElevationEnricher.enrich()`; on failure log warning and skip
    - Compute metrics and create `RouteSegment` for each enriched way
    - Return `Failure` if zero segments were produced
    - Default endpoint: `https://overpass-api.de/api/interpreter`
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 9.1, 9.2, 9.3, 9.4_

  - [ ]* 14.2 Write unit tests for OsmFetcher
    - `buildQuery()` produces correct Overpass QL for a given bbox and filter list
    - `buildQuery()` with default filters produces the expected union of all default types
    - Parsing a valid Overpass JSON response produces the correct number of RouteSegments
    - Parsing a response with zero ways returns `Failure`
    - _Requirements: 8.1, 8.2, 8.5, 9.3_

- [ ] 15. Update GpxPrettyPrinter with source metadata
  - Add overloaded `write(Route, Metrics, Set<String> sources, Path)` and `toGpxString(Route, Metrics, Set<String> sources)` methods
  - When `sources` is non-empty, write `<src>GPX library, OpenStreetMap</src>` inside `<metadata>`
  - Existing zero-argument-sources methods remain unchanged (backward compatible)
  - _Requirements: 11.4_

- [ ] 16. Update Main CLI for OSM mode
  - Parse `--osm-bbox` argument (format: `min_lat,min_lon,max_lat,max_lon`)
  - Parse repeatable `--osm-filter` arguments; validate each via `OsmSegmentFilter.fromCliString()`
  - Make `--library` optional when `--osm-bbox` is provided
  - Validate bbox: exactly 4 comma-separated doubles, min < max, lat ∈ [-90,90], lon ∈ [-180,180]
  - If `--osm-bbox` present, call `OsmFetcher.fetch()` and merge OSM segments with any GPX segments
  - If neither `--library` nor `--osm-bbox` is provided, print error and exit 1
  - Pass active sources set to `GpxPrettyPrinter`
  - On success in OSM mode, print number of OSM segments fetched
  - Update usage message to reflect optional `--library` and new OSM arguments
  - _Requirements: 11.1, 11.2, 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 12.7_

- [ ] 17. Final checkpoint — Ensure all tests pass
  - Run `mvn test` and verify all existing tests still pass with the updated code
  - Fix any regressions introduced by the OSM changes
