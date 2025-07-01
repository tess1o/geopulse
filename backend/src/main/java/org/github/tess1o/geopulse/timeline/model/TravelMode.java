package org.github.tess1o.geopulse.timeline.model;

public enum TravelMode {
    WALKING,
    CAR,
    UNKNOWN;

    // Thresholds (can be adjusted)
    private static final double WALKING_MAX_AVG_SPEED = 8.0;
    private static final double WALKING_MAX_MAX_SPEED = 10.0;

    private static final double CAR_MIN_AVG_SPEED = 10.0;
    private static final double CAR_MIN_MAX_SPEED = 30.0;
    public static final double SHORT_DISTANCE_KM = 1.5;

    public static TravelMode classify(double avgSpeedKmh, double maxSpeedKmh, double totalDistanceKm) {
        // Short trip walking tolerance
        boolean shortTrip = totalDistanceKm <= SHORT_DISTANCE_KM;
        boolean avgSpeedWithinWalking = avgSpeedKmh < WALKING_MAX_AVG_SPEED;
        boolean maxSpeedWithinWalking = maxSpeedKmh < WALKING_MAX_MAX_SPEED;
        boolean avgSpeedSlightlyAboveWalking = avgSpeedKmh < (WALKING_MAX_AVG_SPEED + 1.0); // 1 km/h delta

        if ((avgSpeedWithinWalking && maxSpeedWithinWalking) ||
                (shortTrip && avgSpeedSlightlyAboveWalking && maxSpeedWithinWalking)) {
            return WALKING;
        } else if (avgSpeedKmh > CAR_MIN_AVG_SPEED || maxSpeedKmh > CAR_MIN_MAX_SPEED) {
            return CAR;
        } else {
            return UNKNOWN;
        }
    }
}