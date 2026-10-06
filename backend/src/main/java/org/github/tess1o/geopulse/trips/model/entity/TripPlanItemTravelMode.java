package org.github.tess1o.geopulse.trips.model.entity;

/**
 * How the traveller reaches a stop from the previous one in the plan. Absent (null) means
 * automatic: the mode is picked from the distance between the two stops.
 */
public enum TripPlanItemTravelMode {
    WALK,
    BICYCLE,
    DRIVE,
    /** No route: drawn as a straight line (flights, ferries, trains). */
    STRAIGHT
}
