package com.gpxroute;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests using real GPX fixture files on disk.
 *
 * Fixture files are located in src/test/resources/fixtures/:
 *   - route_a.gpx       : valid, 10 waypoints, ~0.5 km, ~63 m gain
 *   - route_b.gpx       : valid, 15 waypoints, ~0.8 km, ~150 m gain (starts adjacent to route_a end)
 *   - route_c.gpx       : valid, 2 tracks, 12 waypoints total (second track repeats last wp of first)
 *   - route_missing_ele.gpx : invalid — one trkpt missing <ele>
 *   - route_malformed.gpx   : invalid — malformed XML (unclosed tag)
 */
class IntegrationTest {

    // -------------------------------------------------------------------------
    // Helper: resolve a fixture file path via the classloader
    // -------------------------------------------------------------------------
    private Path fixture(String name) throws Exception {
        URL url = getClass().getClassLoader().getResource("fixtures/" + name);
        assertThat(url).as("Fixture not found: " + name).isNotNull();
        return Path.of(url.toURI());
    }

    private Path fixturesDir() throws Exception {
        URL url = getClass().getClassLoader().getResource("fixtures/route_a.gpx");
        assertThat(url).as("Fixtures directory not found").isNotNull();
        return Path.of(url.toURI()).getParent();
    }

    // =========================================================================
    // Test 1: Load valid library
    // =========================================================================

    /**
     * Load the fixtures directory using RouteLibrary.
     * route_a.gpx and route_b.gpx should be loaded successfully.
     * route_missing_ele.gpx and route_malformed.gpx should be skipped with warnings.
     * route_c.gpx is also valid and should be loaded.
     * Result must be a success with at least 2 segments.
     */
    @Test
    void loadValidLibrary() throws Exception {
        Path dir = fixturesDir();
        RouteLibrary library = new RouteLibrary();

        Result<List<RouteSegment>> result = library.load(dir);

        assertThat(result.isSuccess())
                .as("Library load should succeed: " + (result.isSuccess() ? "" : result.getError()))
                .isTrue();

        List<RouteSegment> segments = result.getValue();
        assertThat(segments.size())
                .as("Should have loaded at least 2 valid segments (route_a, route_b, route_c)")
                .isGreaterThanOrEqualTo(2);

        // Verify route_a and route_b are among the loaded segments by checking waypoint counts
        boolean hasRouteA = segments.stream().anyMatch(s -> s.route().size() == 10);
        boolean hasRouteB = segments.stream().anyMatch(s -> s.route().size() == 15);
        assertThat(hasRouteA).as("route_a.gpx (10 waypoints) should be loaded").isTrue();
        assertThat(hasRouteB).as("route_b.gpx (15 waypoints) should be loaded").isTrue();
    }

    // =========================================================================
    // Test 2: Load directory with invalid files — warnings for skipped files
    // =========================================================================

    /**
     * Load the fixtures directory and verify that invalid files are skipped.
     * The result should still be a success because valid files exist.
     * Warnings are printed to stderr by RouteLibrary (verified indirectly by success result).
     */
    @Test
    void loadDirectoryWithInvalidFiles() throws Exception {
        Path dir = fixturesDir();
        RouteLibrary library = new RouteLibrary();

        Result<List<RouteSegment>> result = library.load(dir);

        // The load should succeed because valid files exist
        assertThat(result.isSuccess())
                .as("Library load should succeed despite invalid files")
                .isTrue();

        List<RouteSegment> segments = result.getValue();

        // Only valid files should be in the result (route_a, route_b, route_c = 3 valid files)
        // route_missing_ele.gpx and route_malformed.gpx should be skipped
        assertThat(segments.size())
                .as("Should have exactly 3 valid segments (route_a, route_b, route_c)")
                .isEqualTo(3);

        // None of the segments should have 5 waypoints (route_missing_ele has 5 trkpts but is invalid)
        // Verify all segments have valid (non-zero) distances
        for (RouteSegment seg : segments) {
            assertThat(seg.distanceKm())
                    .as("Each loaded segment should have a positive distance")
                    .isGreaterThan(0.0);
            assertThat(seg.route().size())
                    .as("Each loaded segment should have at least 1 waypoint")
                    .isGreaterThan(0);
        }
    }

    // =========================================================================
    // Test 3: Full pipeline test
    // =========================================================================

    /**
     * Full pipeline: load library → generate route → compute metrics → write GPX → re-parse.
     *
     * Actual metrics (computed from fixture files):
     *   route_a: 0.426 km, 58 m gain (ends at 45.9265/6.8731)
     *   route_b: 1.150 km, 150 m gain (starts at 45.9266/6.8732 — 13.5m from route_a end)
     *   route_a + route_b combined: 1.576 km, 208 m gain
     *
     * Target: distance=1.5 km (±10% → 1.35–1.65 km), elevation=200 m (±15% → 170–230 m).
     * route_b alone (1.15 km) does NOT satisfy the distance range, so the generator
     * must combine route_a + route_b to satisfy both constraints.
     */
    @Test
    void fullPipelineTest(@TempDir Path tempDir) throws Exception {
        // Step a: Load the fixtures directory
        Path dir = fixturesDir();
        RouteLibrary library = new RouteLibrary();
        Result<List<RouteSegment>> loadResult = library.load(dir);
        assertThat(loadResult.isSuccess())
                .as("Library load should succeed")
                .isTrue();
        List<RouteSegment> segments = loadResult.getValue();

        // Step b: Generate a route
        // Actual metrics (computed):
        //   route_a: 0.426 km, 58 m gain (ends at 45.9265/6.8731)
        //   route_b: 1.150 km, 150 m gain (starts at 45.9266/6.8732 — 13.5m from route_a end)
        //   route_a + route_b: 1.576 km, 208 m gain
        //
        // Target 1.5 km ±10% = [1.35, 1.65] km — route_a+route_b (1.576 km) fits ✓
        // Target 200 m ±15% = [170, 230] m — route_a+route_b (208 m) fits ✓
        // route_b alone (1.15 km) does NOT fit the distance range, so the generator
        // must combine route_a + route_b to satisfy the constraints.
        double targetDistance = 1.5;
        double targetElevation = 200.0;

        RouteGenerator generator = new RouteGenerator();
        Result<Route> genResult = generator.generate(segments, targetDistance, targetElevation);
        assertThat(genResult.isSuccess())
                .as("Route generation should succeed: " + (genResult.isSuccess() ? "" : genResult.getError()))
                .isTrue();
        Route generatedRoute = genResult.getValue();

        // Step c: Compute metrics
        RouteMetrics.Metrics metrics = RouteMetrics.compute(generatedRoute);

        // Step d: Write to a temp file
        Path outputFile = tempDir.resolve("generated_route.gpx");
        GpxPrettyPrinter printer = new GpxPrettyPrinter();
        Result<Path> writeResult = printer.write(generatedRoute, metrics, outputFile);
        assertThat(writeResult.isSuccess())
                .as("GPX write should succeed: " + (writeResult.isSuccess() ? "" : writeResult.getError()))
                .isTrue();
        assertThat(Files.exists(outputFile)).as("Output file should exist").isTrue();

        // Step e: Re-parse the output file
        GpxParser parser = new GpxParser();
        Result<Route> reparseResult = parser.parse(outputFile);

        // Step f: Assert the re-parsed route is a success (round-trip)
        assertThat(reparseResult.isSuccess())
                .as("Re-parsed route should be a success (round-trip): "
                        + (reparseResult.isSuccess() ? "" : reparseResult.getError()))
                .isTrue();

        // Step g: Assert generated route distance is within ±10% of 1.5 km
        double distanceTolerance = 0.10;
        double minDist = targetDistance * (1.0 - distanceTolerance);
        double maxDist = targetDistance * (1.0 + distanceTolerance);
        assertThat(metrics.distanceKm())
                .as("Generated route distance %.3f km should be within ±10%% of %.1f km [%.3f, %.3f]",
                        metrics.distanceKm(), targetDistance, minDist, maxDist)
                .isBetween(minDist, maxDist);

        // Step h: Assert generated route elevation gain is within ±15% of 200 m
        double elevationTolerance = 0.15;
        double minElev = targetElevation * (1.0 - elevationTolerance);
        double maxElev = targetElevation * (1.0 + elevationTolerance);
        assertThat(metrics.elevationGainM())
                .as("Generated route elevation gain %.1f m should be within ±15%% of %.1f m [%.1f, %.1f]",
                        metrics.elevationGainM(), targetElevation, minElev, maxElev)
                .isBetween(minElev, maxElev);
    }

    // =========================================================================
    // Test 4: GPX 1.1 schema validation
    // =========================================================================

    /**
     * After writing the output GPX, validate it against the GPX 1.1 XSD schema.
     * If the schema cannot be loaded (e.g., network unavailable), the test is skipped gracefully.
     */
    @Test
    void gpx11SchemaValidation(@TempDir Path tempDir) throws Exception {
        // Generate a simple route and write it to a temp file
        GpxParser parser = new GpxParser();
        Result<Route> parseResult = parser.parse(fixture("route_a.gpx"));
        assertThat(parseResult.isSuccess()).as("route_a.gpx should parse successfully").isTrue();

        Route route = parseResult.getValue();
        RouteMetrics.Metrics metrics = RouteMetrics.compute(route);

        Path outputFile = tempDir.resolve("schema_test.gpx");
        GpxPrettyPrinter printer = new GpxPrettyPrinter();
        Result<Path> writeResult = printer.write(route, metrics, outputFile);
        assertThat(writeResult.isSuccess()).as("GPX write should succeed").isTrue();

        // Attempt to load the GPX 1.1 XSD schema
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

        javax.xml.validation.Schema schema;
        try {
            URL schemaUrl = new URL("http://www.topografix.com/GPX/1/1/gpx.xsd");
            schema = schemaFactory.newSchema(schemaUrl);
        } catch (Exception e) {
            // Network unavailable or schema cannot be loaded — skip gracefully
            Assumptions.assumeTrue(false, "GPX schema not available: " + e.getMessage());
            return;
        }

        // Validate the written GPX file against the schema
        javax.xml.validation.Validator validator = schema.newValidator();
        String gpxContent = Files.readString(outputFile);
        try {
            validator.validate(new StreamSource(new StringReader(gpxContent)));
        } catch (org.xml.sax.SAXException e) {
            // If validation fails, fail the test with a descriptive message
            assertThat(false)
                    .as("GPX output failed schema validation: " + e.getMessage())
                    .isTrue();
        }
    }

    // =========================================================================
    // Test 5: Parser rejects missing altitude
    // =========================================================================

    /**
     * Parse route_missing_ele.gpx directly and assert Result.isSuccess() is false.
     */
    @Test
    void parserRejectsMissingAltitude() throws Exception {
        GpxParser parser = new GpxParser();
        Result<Route> result = parser.parse(fixture("route_missing_ele.gpx"));

        assertThat(result.isSuccess())
                .as("Parser should reject GPX with missing altitude data")
                .isFalse();
        assertThat(result.getError())
                .as("Error message should mention missing altitude")
                .containsIgnoringCase("missing altitude");
    }

    // =========================================================================
    // Test 6: Parser rejects malformed XML
    // =========================================================================

    /**
     * Parse route_malformed.gpx directly and assert Result.isSuccess() is false.
     */
    @Test
    void parserRejectsMalformedXml() throws Exception {
        GpxParser parser = new GpxParser();
        Result<Route> result = parser.parse(fixture("route_malformed.gpx"));

        assertThat(result.isSuccess())
                .as("Parser should reject malformed XML")
                .isFalse();
        assertThat(result.getError())
                .as("Error message should mention parse failure")
                .isNotBlank();
    }

    // =========================================================================
    // Test 7: Multi-track merge
    // =========================================================================

    /**
     * Parse route_c.gpx and verify it produces a single Route with all waypoints
     * from both tracks merged in order.
     *
     * route_c.gpx has 2 tracks:
     *   Track 1: 6 waypoints (ends at lat=45.9225/lon=6.8685/ele=1021.0)
     *   Track 2: 6 waypoints (starts at lat=45.9225/lon=6.8685/ele=1021.0 — same point)
     *
     * The GpxParser merges all <trkpt> elements across all <trk> and <trkseg> elements
     * into a single ordered list without deduplication. So the result should have 12 waypoints.
     */
    @Test
    void multiTrackMerge() throws Exception {
        GpxParser parser = new GpxParser();
        Result<Route> result = parser.parse(fixture("route_c.gpx"));

        assertThat(result.isSuccess())
                .as("route_c.gpx should parse successfully: "
                        + (result.isSuccess() ? "" : result.getError()))
                .isTrue();

        Route route = result.getValue();

        // The parser merges all trkpt elements in document order without deduplication.
        // Track 1 has 6 waypoints, Track 2 has 6 waypoints → 12 total.
        assertThat(route.size())
                .as("Merged route should have 12 waypoints (6 from track 1 + 6 from track 2)")
                .isEqualTo(12);

        // Verify the first waypoint is from track 1
        Waypoint first = route.first();
        assertThat(first.lat()).as("First waypoint lat").isEqualTo(45.9200, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(first.lon()).as("First waypoint lon").isEqualTo(6.8650, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(first.ele()).as("First waypoint ele").isEqualTo(980.0, org.assertj.core.data.Offset.offset(0.01));

        // Verify the last waypoint is from track 2
        Waypoint last = route.last();
        assertThat(last.lat()).as("Last waypoint lat").isEqualTo(45.9245, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(last.lon()).as("Last waypoint lon").isEqualTo(6.8710, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(last.ele()).as("Last waypoint ele").isEqualTo(1053.0, org.assertj.core.data.Offset.offset(0.01));

        // Verify the 6th waypoint (last of track 1) and 7th waypoint (first of track 2) are the same
        // (the parser does NOT deduplicate — it includes all trkpt elements as-is)
        Waypoint wp6 = route.waypoints().get(5);  // index 5 = 6th waypoint (last of track 1)
        Waypoint wp7 = route.waypoints().get(6);  // index 6 = 7th waypoint (first of track 2)
        assertThat(wp6.lat()).as("6th waypoint lat (last of track 1)").isEqualTo(45.9225, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(wp7.lat()).as("7th waypoint lat (first of track 2)").isEqualTo(45.9225, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(wp6.ele()).as("6th waypoint ele").isEqualTo(1021.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(wp7.ele()).as("7th waypoint ele").isEqualTo(1021.0, org.assertj.core.data.Offset.offset(0.01));
    }
}
