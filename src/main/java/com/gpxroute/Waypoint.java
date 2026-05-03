package com.gpxroute;

/**
 * A single GPS coordinate with altitude.
 */
public record Waypoint(double lat, double lon, double ele) {}
