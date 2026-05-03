package com.gpxroute;

/**
 * A route loaded from the library, with pre-computed metrics.
 */
public record RouteSegment(Route route, double distanceKm, double elevationGainM) {}
