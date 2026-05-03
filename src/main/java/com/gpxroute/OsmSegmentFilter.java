package com.gpxroute;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Supported OSM way-type filters for trail running route generation.
 */
public enum OsmSegmentFilter {
    FOOTWAY("highway", "footway"),
    PATH("highway", "path"),
    TRACK("highway", "track"),
    BRIDLEWAY("highway", "bridleway"),
    STEPS("highway", "steps"),
    HIKING("route", "hiking");

    private final String key;
    private final String value;

    OsmSegmentFilter(String key, String value) {
        this.key = key;
        this.value = value;
    }

    /** Returns the CLI string representation, e.g. "highway=footway". */
    public String toCliString() {
        return key + "=" + value;
    }

    /** Returns the Overpass QL tag filter expression, e.g. ["highway"="footway"]. */
    public String toOverpassTag() {
        return "[\"" + key + "\"=\"" + value + "\"]";
    }

    /**
     * Parses a CLI string like "highway=footway" into an OsmSegmentFilter.
     * Returns Result.failure with a list of valid values if unrecognised.
     */
    public static Result<OsmSegmentFilter> fromCliString(String s) {
        for (OsmSegmentFilter f : values()) {
            if (f.toCliString().equals(s)) return Result.success(f);
        }
        String valid = Arrays.stream(values())
                .map(OsmSegmentFilter::toCliString)
                .collect(Collectors.joining(", "));
        return Result.failure("Unknown filter '" + s + "'. Valid values: " + valid);
    }

    /** Default filter set applied when no --osm-filter is specified. */
    public static List<OsmSegmentFilter> defaults() {
        return List.of(FOOTWAY, PATH, TRACK, BRIDLEWAY, HIKING);
    }
}
