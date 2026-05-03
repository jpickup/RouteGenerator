# Requirements Document

## Introduction

The GPX Route Generator is an application that creates new trail running route files in GPX format by composing and adapting segments from a library of existing GPX route files. Given a desired total distance and elevation gain, the system produces a valid GPX file representing a runnable trail route that meets the user's parameters within acceptable tolerances.

## Glossary

- **GPX**: GPS Exchange Format — an XML-based file format for storing GPS track, route, and waypoint data.
- **Route**: An ordered sequence of waypoints forming a trail running path, stored as a GPX file.
- **Segment**: A contiguous sub-section of a route, defined by a start and end waypoint within a GPX track.
- **Library**: The collection of existing GPX files from which segments are sourced.
- **Distance**: The total length of a route in kilometers, calculated along the track path.
- **Elevation Gain**: The cumulative sum of all uphill altitude changes along a route, measured in meters.
- **Elevation Loss**: The cumulative sum of all downhill altitude changes along a route, measured in meters.
- **Tolerance**: The acceptable deviation between a requested parameter value and the generated route's actual value.
- **Waypoint**: A single GPS coordinate consisting of latitude, longitude, and altitude.
- **Track**: An ordered list of waypoints representing a recorded path in a GPX file.
- **Generator**: The component responsible for composing new routes from library segments.
- **Parser**: The component responsible for reading and interpreting GPX files.
- **Pretty_Printer**: The component responsible for serializing route data back into valid GPX XML.
- **Validator**: The component responsible for verifying GPX file structure and route parameter compliance.
- **OSM**: OpenStreetMap — a free, editable map of the world whose geographic data is available under the Open Database Licence.
- **Overpass_API**: A read-only HTTP API for querying OpenStreetMap data by geographic area and tag filters.
- **Bounding_Box**: A rectangular geographic area defined by minimum and maximum latitude and longitude values (min_lat, min_lon, max_lat, max_lon).
- **OSM_Way**: An ordered list of OSM nodes forming a linear feature such as a path, track, or road in OpenStreetMap data.
- **OSM_Node**: A single geographic point in OpenStreetMap data, defined by latitude and longitude (no altitude).
- **Way_Type_Filter**: A set of OSM tag key–value pairs (e.g. `highway=footway`) used to restrict which OSM ways are fetched.
- **Elevation_Service**: An external HTTP service that returns altitude values for a list of geographic coordinates (latitude, longitude).
- **OSM_Fetcher**: The component responsible for querying the Overpass API and converting the response into RouteSegments.
- **Elevation_Enricher**: The component responsible for querying an Elevation_Service and attaching altitude values to waypoints that lack them.
- **Open_Elevation**: A free, open-source elevation API compatible with the SRTM dataset, used as the default Elevation_Service.

---

## Requirements

### Requirement 1: Parse GPX Library Files

**User Story:** As a trail runner, I want the app to read my existing GPX files, so that it can use my real routes as the basis for generating new ones.

#### Acceptance Criteria

1. WHEN a valid GPX file is provided, THE Parser SHALL parse it into an internal Route representation containing all waypoints with latitude, longitude, and altitude.
2. WHEN a GPX file contains multiple tracks or track segments, THE Parser SHALL merge them into a single ordered sequence of waypoints.
3. IF a GPX file is malformed or does not conform to the GPX 1.1 schema, THEN THE Parser SHALL return a descriptive error identifying the file and the nature of the violation.
4. IF a GPX file contains waypoints with missing altitude data, THEN THE Parser SHALL return a descriptive error, as elevation data is required for route generation.
5. THE Pretty_Printer SHALL serialize a Route representation back into a valid GPX 1.1 XML file.
6. FOR ALL valid Route objects, parsing then printing then parsing SHALL produce an equivalent Route object with identical waypoints (round-trip property).

---

### Requirement 2: Index the Route Library

**User Story:** As a trail runner, I want the app to load all my GPX files from a folder, so that I don't have to specify files individually.

#### Acceptance Criteria

1. WHEN a directory path is provided, THE Generator SHALL load and parse all GPX files found in that directory.
2. WHEN a directory contains no GPX files, THE Generator SHALL return an error indicating the library is empty.
3. IF a file in the directory fails to parse, THEN THE Generator SHALL log a warning for that file and continue loading the remaining files.
4. THE Generator SHALL compute and store the total distance and total elevation gain for each loaded route.

---

### Requirement 3: Calculate Route Metrics

**User Story:** As a trail runner, I want the app to accurately measure distance and elevation from GPS data, so that generated routes match my requested parameters.

#### Acceptance Criteria

1. WHEN a Route is provided, THE Generator SHALL calculate its total distance in kilometers using the Haversine formula applied to consecutive waypoints.
2. WHEN a Route is provided, THE Generator SHALL calculate its total elevation gain in meters by summing all positive altitude differences between consecutive waypoints.
3. WHEN a Route is provided, THE Generator SHALL calculate its total elevation loss in meters by summing all negative altitude differences between consecutive waypoints.
4. THE Generator SHALL apply a smoothing threshold of 5 meters to elevation differences to filter GPS noise before computing elevation gain and loss.

---

### Requirement 4: Generate a Route Matching Requested Parameters

**User Story:** As a trail runner, I want to request a route of a specific distance and elevation gain, so that I can plan training runs that match my goals.

#### Acceptance Criteria

1. WHEN a target distance in kilometers and a target elevation gain in meters are provided, THE Generator SHALL produce a GPX route whose total distance is within ±10% of the requested distance.
2. WHEN a target distance in kilometers and a target elevation gain in meters are provided, THE Generator SHALL produce a GPX route whose total elevation gain is within ±15% of the requested elevation gain.
3. THE Generator SHALL compose the output route by selecting and concatenating segments from the library, ensuring geographic continuity between consecutive segments.
4. WHEN no combination of library segments can satisfy the requested parameters within tolerance, THE Generator SHALL return an error describing the closest achievable values and the shortfall.
5. THE Generator SHALL ensure the generated route does not contain duplicate consecutive waypoints.

---

### Requirement 5: Ensure Geographic Continuity

**User Story:** As a trail runner, I want generated routes to be geographically connected, so that the route is actually runnable without teleporting between segments.

#### Acceptance Criteria

1. WHEN two segments are joined, THE Generator SHALL only connect them if the end waypoint of the first segment is within 50 meters of the start waypoint of the second segment.
2. IF no geographically adjacent segment can be found to extend the current route, THEN THE Generator SHALL backtrack and attempt an alternative segment selection.
3. THE Generator SHALL not reuse the same segment more than once within a single generated route.

---

### Requirement 6: Export the Generated Route

**User Story:** As a trail runner, I want to save the generated route as a GPX file, so that I can load it into my GPS watch or mapping app.

#### Acceptance Criteria

1. WHEN a route is successfully generated, THE Generator SHALL write it to a GPX file at a user-specified output path.
2. THE Pretty_Printer SHALL produce GPX output that is valid according to the GPX 1.1 XML schema.
3. THE Pretty_Printer SHALL include route metadata in the GPX output, containing the total distance in kilometers and total elevation gain in meters as GPX `<desc>` content.
4. IF the output file path is not writable, THEN THE Generator SHALL return an error identifying the path and the reason for the failure.

---

### Requirement 7: Command-Line Interface

**User Story:** As a trail runner, I want to run the tool from the command line, so that I can integrate it into my training workflow without needing a GUI.

#### Acceptance Criteria

1. THE Generator SHALL accept the following command-line arguments: `--library` (path to GPX library directory), `--distance` (target distance in kilometers), `--elevation` (target elevation gain in meters), and `--output` (output GPX file path).
2. IF any required argument is missing, THEN THE Generator SHALL print a usage message listing all required arguments and exit with a non-zero status code.
3. IF an argument value is not a valid number where a number is expected, THEN THE Generator SHALL print a descriptive error message and exit with a non-zero status code.
4. WHEN route generation succeeds, THE Generator SHALL print a summary to standard output including the output file path, actual distance, and actual elevation gain of the generated route.
5. WHEN route generation fails, THE Generator SHALL print a descriptive error message to standard error and exit with a non-zero status code.

---

### Requirement 8: Query OpenStreetMap Ways via the Overpass API

**User Story:** As a trail runner, I want the app to fetch path and trail data from OpenStreetMap, so that I can generate routes in areas where I have no existing GPX files.

#### Acceptance Criteria

1. WHEN a Bounding_Box (min_lat, min_lon, max_lat, max_lon) is provided, THE OSM_Fetcher SHALL submit a query to the Overpass API and retrieve all OSM_Ways within that area that match the active Way_Type_Filter.
2. WHEN the Overpass API returns a successful response, THE OSM_Fetcher SHALL convert each returned OSM_Way into a RouteSegment whose waypoints follow the ordered sequence of OSM_Nodes in that way.
3. IF the Overpass API returns an HTTP error or a network timeout, THEN THE OSM_Fetcher SHALL return a descriptive error identifying the HTTP status code or timeout condition.
4. IF the Overpass API response is not valid JSON or does not conform to the expected Overpass JSON schema, THEN THE OSM_Fetcher SHALL return a descriptive error.
5. IF the Overpass API query returns zero ways matching the filter, THEN THE OSM_Fetcher SHALL return an error indicating that no matching ways were found in the specified area.
6. THE OSM_Fetcher SHALL use the public Overpass API endpoint `https://overpass-api.de/api/interpreter` as the default query target.

---

### Requirement 9: Filter OSM Ways by Type

**User Story:** As a trail runner, I want to restrict which kinds of paths are fetched from OSM, so that I only get footpaths, trails, and bridleways rather than roads.

#### Acceptance Criteria

1. THE OSM_Fetcher SHALL support filtering OSM ways by the following tag values: `highway=footway`, `highway=path`, `highway=track`, `highway=bridleway`, `highway=steps`, and `route=hiking`.
2. WHEN one or more Way_Type_Filter values are specified, THE OSM_Fetcher SHALL include only OSM_Ways whose tags match at least one of the specified filter values.
3. WHEN no Way_Type_Filter is specified, THE OSM_Fetcher SHALL apply a default filter equivalent to `highway=footway OR highway=path OR highway=track OR highway=bridleway OR route=hiking`.
4. THE OSM_Fetcher SHALL accept multiple filter values simultaneously and combine them as a logical OR in the Overpass query.
5. IF an unrecognised filter value is provided, THEN THE OSM_Fetcher SHALL return a descriptive error listing the valid filter values.

---

### Requirement 10: Enrich OSM Waypoints with Elevation Data

**User Story:** As a trail runner, I want OSM-sourced segments to include realistic elevation data, so that the route generator can accurately match my target elevation gain.

#### Acceptance Criteria

1. WHEN an OSM_Way is converted into a RouteSegment, THE Elevation_Enricher SHALL query the Elevation_Service with the latitude and longitude of every waypoint in that segment and attach the returned altitude value to each waypoint.
2. WHEN the Elevation_Service returns altitude values for all requested coordinates, THE Elevation_Enricher SHALL assign each returned altitude to the corresponding waypoint in the same order as the request.
3. IF the Elevation_Service returns an HTTP error or a network timeout, THEN THE Elevation_Enricher SHALL return a descriptive error and SHALL NOT produce a RouteSegment with missing altitude values.
4. IF the Elevation_Service returns a null or absent altitude value for any coordinate, THEN THE Elevation_Enricher SHALL return a descriptive error for that segment.
5. THE Elevation_Enricher SHALL use the Open_Elevation public API endpoint `https://api.open-elevation.com/api/v1/lookup` as the default Elevation_Service.
6. THE Elevation_Enricher SHALL batch coordinate lookups into requests of at most 100 coordinates each to respect Elevation_Service request size limits.

---

### Requirement 11: Integrate OSM Segments with the GPX Library

**User Story:** As a trail runner, I want the route generator to treat OSM-sourced segments and my existing GPX segments as a single pool, so that I can generate routes that mix both sources.

#### Acceptance Criteria

1. WHEN OSM mode is active, THE Generator SHALL merge the RouteSegments produced by the OSM_Fetcher with any RouteSegments loaded from the GPX library into a single combined segment pool before route generation begins.
2. WHEN OSM mode is active and no `--library` path is provided, THE Generator SHALL use only the OSM-sourced segments as the segment pool.
3. THE Generator SHALL apply the same geographic continuity rules (Requirement 5) and backtracking algorithm (Requirement 4) to the combined segment pool regardless of each segment's source.
4. WHEN a generated route is exported, THE Pretty_Printer SHALL include a `<src>` element in the GPX `<metadata>` block listing the data sources used (e.g. `GPX library`, `OpenStreetMap`).

---

### Requirement 12: OSM Mode Command-Line Arguments

**User Story:** As a trail runner, I want to activate OSM mode and configure it from the command line, so that I can fetch and use OSM trail data without editing any configuration files.

#### Acceptance Criteria

1. THE Generator SHALL accept the following additional command-line arguments when OSM mode is used: `--osm-bbox` (bounding box as `min_lat,min_lon,max_lat,max_lon`), and `--osm-filter` (one or more Way_Type_Filter values, repeatable).
2. WHEN `--osm-bbox` is provided, THE Generator SHALL activate OSM mode and use the supplied Bounding_Box for the Overpass API query.
3. IF `--osm-bbox` is provided but does not contain exactly four comma-separated numeric values, THEN THE Generator SHALL print a descriptive error message and exit with a non-zero status code.
4. IF the parsed Bounding_Box values do not satisfy `min_lat < max_lat` and `min_lon < max_lon`, THEN THE Generator SHALL print a descriptive error message and exit with a non-zero status code.
5. IF latitude values are outside the range [-90, 90] or longitude values are outside the range [-180, 180], THEN THE Generator SHALL print a descriptive error message and exit with a non-zero status code.
6. WHEN `--osm-filter` is provided one or more times, THE Generator SHALL pass all supplied values as the Way_Type_Filter to the OSM_Fetcher.
7. WHEN route generation succeeds in OSM mode, THE Generator SHALL include the number of OSM segments fetched in the summary printed to standard output.
