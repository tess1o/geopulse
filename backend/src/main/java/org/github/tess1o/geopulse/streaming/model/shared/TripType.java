package org.github.tess1o.geopulse.streaming.model.shared;

/**
 * Enumeration of trip types that can be classified by analyzing GPS movement patterns.
 * Trip classification is based on speed analysis and movement characteristics.
 */
public enum TripType {
    /**
     * Walking trip - characterized by low speeds and human movement patterns.
     */
    WALK,
    
    /**
     * Car trip - characterized by higher speeds and vehicular movement patterns.
     */
    CAR,
    
    /**
     * Unknown trip type - used when classification cannot be determined reliably
     * due to insufficient data or ambiguous speed patterns.
     */
    UNKNOWN;

    // Thresholds (can be adjusted)
    private static final double WALKING_MAX_AVG_SPEED = 8.0;
    private static final double WALKING_MAX_MAX_SPEED = 10.0;

    private static final double CAR_MIN_AVG_SPEED = 10.0;
    private static final double CAR_MIN_MAX_SPEED = 30.0;
    public static final double SHORT_DISTANCE_KM = 1.5;

    /**
     * Classify trip type based on speed and distance metrics.
     *
     * @param avgSpeedKmh average speed in km/h
     * @param maxSpeedKmh maximum speed in km/h
     * @param totalDistanceKm total distance in km
     * @return classified trip type
     */
    public static TripType classify(double avgSpeedKmh, double maxSpeedKmh, double totalDistanceKm) {
        // Short trip walking tolerance
        boolean shortTrip = totalDistanceKm <= SHORT_DISTANCE_KM;
        boolean avgSpeedWithinWalking = avgSpeedKmh < WALKING_MAX_AVG_SPEED;
        boolean maxSpeedWithinWalking = maxSpeedKmh < WALKING_MAX_MAX_SPEED;
        boolean avgSpeedSlightlyAboveWalking = avgSpeedKmh < (WALKING_MAX_AVG_SPEED + 1.0); // 1 km/h delta

        if ((avgSpeedWithinWalking && maxSpeedWithinWalking) ||
                (shortTrip && avgSpeedSlightlyAboveWalking && maxSpeedWithinWalking)) {
            return WALK;
        } else if (avgSpeedKmh > CAR_MIN_AVG_SPEED || maxSpeedKmh > CAR_MIN_MAX_SPEED) {
            return CAR;
        } else {
            return UNKNOWN;
        }
    }
}