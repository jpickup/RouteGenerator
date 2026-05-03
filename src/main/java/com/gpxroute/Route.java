package com.gpxroute;

import java.util.List;

/**
 * An ordered sequence of waypoints representing a route or segment.
 * Immutable.
 */
public record Route(List<Waypoint> waypoints) {

    public Route {
        waypoints = List.copyOf(waypoints); // defensive copy
    }

    public boolean isEmpty() {
        return waypoints.isEmpty();
    }

    public int size() {
        return waypoints.size();
    }

    public Waypoint first() {
        return waypoints.get(0);
    }

    public Waypoint last() {
        return waypoints.get(waypoints.size() - 1);
    }
}
