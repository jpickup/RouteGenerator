package com.gpxroute;

/**
 * Pure static utility class for computing route statistics.
 * All methods are stateless and operate on immutable domain objects.
 */
public class RouteMetrics {

    /** The smoothing threshold for elevation noise filtering (meters). */
    public static final double ELEVATION_SMOOTHING_THRESHOLD_M = 5.0;

    /** Earth's mean radius in kilometers. */
    public static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Aggregated metrics for a route.
     */
    public record Metrics(double distanceKm, double elevationGainM, double elevationLossM) {}

    // Prevent instantiation
    private RouteMetrics() {}

    /**
     * Computes the Haversine great-circle distance between two waypoints in kilometers.
     *
     * @param a first waypoint
     * @param b second waypoint
     * @return distance in kilometers
     */
    public static double haversineKm(Waypoint a, Waypoint b) {
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());
        double deltaLat = Math.toRadians(b.lat() - a.lat());
        double deltaLon = Math.toRadians(b.lon() - a.lon());

        double sinDLat = Math.sin(deltaLat / 2);
        double sinDLon = Math.sin(deltaLon / 2);

        double aVal = sinDLat * sinDLat
                + Math.cos(lat1) * Math.cos(lat2) * sinDLon * sinDLon;
        double c = 2.0 * Math.atan2(Math.sqrt(aVal), Math.sqrt(1.0 - aVal));

        return EARTH_RADIUS_KM * c;
    }

    /**
     * Calculates total distance in kilometers using the Haversine formula.
     * Returns 0.0 for routes with 0 or 1 waypoints.
     *
     * @param route the route to measure
     * @return total distance in kilometers
     */
    public static double distanceKm(Route route) {
        if (route.size() <= 1) {
            return 0.0;
        }
        double total = 0.0;
        var waypoints = route.waypoints();
        for (int i = 0; i < waypoints.size() - 1; i++) {
            total += haversineKm(waypoints.get(i), waypoints.get(i + 1));
        }
        return total;
    }

    /**
     * Calculates total elevation gain in meters, applying the 5m smoothing threshold.
     * Only positive altitude differences greater than the threshold are counted.
     *
     * @param route the route to measure
     * @return total elevation gain in meters (non-negative)
     */
    public static double elevationGainM(Route route) {
        if (route.size() <= 1) {
            return 0.0;
        }
        double gain = 0.0;
        var waypoints = route.waypoints();
        for (int i = 0; i < waypoints.size() - 1; i++) {
            double delta = waypoints.get(i + 1).ele() - waypoints.get(i).ele();
            if (delta > ELEVATION_SMOOTHING_THRESHOLD_M) {
                gain += delta;
            }
        }
        return gain;
    }

    /**
     * Calculates total elevation loss in meters (returned as a positive value),
     * applying the 5m smoothing threshold.
     * Only negative altitude differences whose absolute value exceeds the threshold are counted.
     *
     * @param route the route to measure
     * @return total elevation loss in meters (non-negative)
     */
    public static double elevationLossM(Route route) {
        if (route.size() <= 1) {
            return 0.0;
        }
        double loss = 0.0;
        var waypoints = route.waypoints();
        for (int i = 0; i < waypoints.size() - 1; i++) {
            double delta = waypoints.get(i + 1).ele() - waypoints.get(i).ele();
            if (delta < -ELEVATION_SMOOTHING_THRESHOLD_M) {
                loss += -delta; // return as positive value
            }
        }
        return loss;
    }

    /**
     * Returns true if two waypoints are within the given distance threshold in meters.
     *
     * @param a               first waypoint
     * @param b               second waypoint
     * @param thresholdMeters maximum distance in meters (inclusive)
     * @return true if haversineKm(a, b) * 1000 &lt;= thresholdMeters
     */
    public static boolean isWithinMeters(Waypoint a, Waypoint b, double thresholdMeters) {
        return haversineKm(a, b) * 1000.0 <= thresholdMeters;
    }

    /**
     * Computes all metrics for a route in a single pass.
     *
     * @param route the route to measure
     * @return a Metrics record containing distance, elevation gain, and elevation loss
     */
    public static Metrics compute(Route route) {
        return new Metrics(distanceKm(route), elevationGainM(route), elevationLossM(route));
    }
}
