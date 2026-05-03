package com.gpxroute;

/**
 * A rectangular geographic area for OSM queries.
 */
public record BoundingBox(double minLat, double minLon, double maxLat, double maxLon) {
    public BoundingBox {
        if (minLat >= maxLat)
            throw new IllegalArgumentException("minLat must be < maxLat, got " + minLat + " >= " + maxLat);
        if (minLon >= maxLon)
            throw new IllegalArgumentException("minLon must be < maxLon, got " + minLon + " >= " + maxLon);
        if (minLat < -90 || maxLat > 90)
            throw new IllegalArgumentException("Latitude out of range [-90, 90]");
        if (minLon < -180 || maxLon > 180)
            throw new IllegalArgumentException("Longitude out of range [-180, 180]");
    }

    /** Returns the Overpass QL bounding box string: "minLat,minLon,maxLat,maxLon" */
    public String toOverpassString() {
        return String.format("%.6f,%.6f,%.6f,%.6f", minLat, minLon, maxLat, maxLon);
    }
}
