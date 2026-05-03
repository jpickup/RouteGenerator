package com.gpxroute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Composes a new route from library segments using depth-first backtracking search.
 */
public class RouteGenerator {

    /** Distance tolerance: ±10% */
    public static final double DISTANCE_TOLERANCE = 0.10;

    /** Elevation tolerance: ±15% */
    public static final double ELEVATION_TOLERANCE = 0.15;

    /** Maximum proximity for joining segments (meters). */
    public static final double SEGMENT_JOIN_THRESHOLD_M = 50.0;

    /**
     * Generates a route matching the target parameters.
     *
     * @param segments             the loaded library segments
     * @param targetDistanceKm     desired total distance in kilometers
     * @param targetElevationGainM desired total elevation gain in meters
     * @return Success with the generated Route, or Failure describing the shortfall
     */
    public Result<Route> generate(
            List<RouteSegment> segments,
            double targetDistanceKm,
            double targetElevationGainM) {

        // Shuffle a copy for variety across runs
        List<RouteSegment> shuffled = new ArrayList<>(segments);
        Collections.shuffle(shuffled);

        // Mutable tracker for the closest (accDist, accElev) seen during search
        double[] closest = {0.0, 0.0};

        List<RouteSegment> chosen = search(
                new ArrayList<>(),
                shuffled,
                0.0, 0.0,
                targetDistanceKm, targetElevationGainM,
                closest);

        if (chosen != null) {
            return Result.success(concatenate(chosen));
        }

        double closestDist = closest[0];
        double closestElev = closest[1];
        double deltaDist = Math.abs(targetDistanceKm - closestDist);
        double deltaElev = Math.abs(targetElevationGainM - closestElev);

        return Result.failure(String.format(
                "Cannot satisfy request: closest achievable distance=%.2fkm, elevation=%.0fm; " +
                "shortfall: distance=%.2fkm, elevation=%.0fm",
                closestDist, closestElev, deltaDist, deltaElev));
    }

    /**
     * Recursive depth-first backtracking search.
     *
     * @param chosen    segments chosen so far
     * @param remaining segments still available
     * @param accDist   accumulated distance in km
     * @param accElev   accumulated elevation gain in meters
     * @param targetDist  target distance in km
     * @param targetElev  target elevation gain in meters
     * @param closest   mutable two-element array [bestDist, bestElev] tracking closest seen
     * @return the list of chosen segments if a solution is found, or null if none
     */
    private List<RouteSegment> search(
            List<RouteSegment> chosen,
            List<RouteSegment> remaining,
            double accDist,
            double accElev,
            double targetDist,
            double targetElev,
            double[] closest) {

        // Update closest tracker if current state is better (closer to target distance)
        double currentDistDelta = Math.abs(targetDist - accDist);
        double bestDistDelta = Math.abs(targetDist - closest[0]);
        if (currentDistDelta < bestDistDelta || (chosen.isEmpty() && closest[0] == 0.0 && closest[1] == 0.0)) {
            closest[0] = accDist;
            closest[1] = accElev;
        }

        // Check if within tolerance
        if (withinTolerance(accDist, accElev, targetDist, targetElev)) {
            return new ArrayList<>(chosen);
        }

        // Prune: overshot distance
        if (accDist > targetDist * (1.0 + DISTANCE_TOLERANCE)) {
            return null;
        }

        // Try each remaining segment
        for (int i = 0; i < remaining.size(); i++) {
            RouteSegment segment = remaining.get(i);

            // Check connectivity: first segment is always allowed;
            // subsequent segments must connect within threshold
            if (!chosen.isEmpty()) {
                Waypoint lastWp = chosen.getLast().route().last();
                Waypoint firstWp = segment.route().first();
                if (!RouteMetrics.isWithinMeters(lastWp, firstWp, SEGMENT_JOIN_THRESHOLD_M)) {
                    continue;
                }
            }

            // Build new chosen and remaining lists
            List<RouteSegment> newChosen = new ArrayList<>(chosen);
            newChosen.add(segment);

            List<RouteSegment> newRemaining = new ArrayList<>(remaining);
            newRemaining.remove(i);

            List<RouteSegment> result = search(
                    newChosen,
                    newRemaining,
                    accDist + segment.distanceKm(),
                    accElev + segment.elevationGainM(),
                    targetDist,
                    targetElev,
                    closest);

            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /**
     * Checks whether the accumulated distance and elevation are within the specified tolerances.
     */
    private boolean withinTolerance(double accDist, double accElev, double targetDist, double targetElev) {
        boolean distOk = accDist >= targetDist * (1.0 - DISTANCE_TOLERANCE)
                && accDist <= targetDist * (1.0 + DISTANCE_TOLERANCE);
        boolean elevOk = accElev >= targetElev * (1.0 - ELEVATION_TOLERANCE)
                && accElev <= targetElev * (1.0 + ELEVATION_TOLERANCE);
        return distOk && elevOk;
    }

    /**
     * Concatenates the waypoints from all chosen segments into a single Route.
     * Removes duplicate waypoints at segment join points (same lat, lon, ele).
     *
     * @param chosen ordered list of segments to concatenate
     * @return a Route containing all waypoints in order
     */
    private Route concatenate(List<RouteSegment> chosen) {
        List<Waypoint> combined = new ArrayList<>();

        for (RouteSegment segment : chosen) {
            List<Waypoint> waypoints = segment.route().waypoints();
            if (combined.isEmpty()) {
                combined.addAll(waypoints);
            } else {
                // Skip the first waypoint of this segment if it duplicates the last of the previous
                Waypoint last = combined.getLast();
                int startIndex = 0;
                if (!waypoints.isEmpty()) {
                    Waypoint first = waypoints.getFirst();
                    if (last.lat() == first.lat()
                            && last.lon() == first.lon()
                            && last.ele() == first.ele()) {
                        startIndex = 1;
                    }
                }
                for (int i = startIndex; i < waypoints.size(); i++) {
                    combined.add(waypoints.get(i));
                }
            }
        }

        return new Route(combined);
    }
}
