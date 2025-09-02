package org.github.tess1o.geopulse.streaming.util;

public final class TimelineConstants {
    
    // Velocity and speed thresholds
    public static final double SUSPICIOUS_SPEED_THRESHOLD_KMH = 170.0;
    public static final double MOVING_AVERAGE_WINDOW_SIZE = 3;

    // Minimum trip criteria
    public static final double MIN_TRIP_DISTANCE_KM = 0.2;


    private TimelineConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}