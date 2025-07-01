package org.github.tess1o.geopulse.timeline.util;

public final class TimelineConstants {
    
    // Default accuracy thresholds
    public static final double DEFAULT_ACCURACY_THRESHOLD = 10.0;
    public static final double HIGH_ACCURACY_FILTER_THRESHOLD = 10.0;
    
    // Velocity and speed thresholds
    public static final double SUSPICIOUS_SPEED_THRESHOLD_KMH = 170.0;
    public static final double MOVING_AVERAGE_WINDOW_SIZE = 3;
    
    // Travel classification thresholds
    public static final double WALKING_MAX_SPEED_KMH = 10.0;
    public static final double WALKING_MEDIAN_THRESHOLD_KMH = 7.0;
    public static final double DRIVING_MIN_SPEED_KMH = 15.0;
    public static final double DRIVING_MEDIAN_THRESHOLD_KMH = 10.0;
    
    // Minimum trip criteria
    public static final long MIN_TRIP_DURATION_SECONDS = 60;
    public static final double MIN_TRIP_DISTANCE_KM = 0.2;
    
    // Stay point merging thresholds
    public static final int MERGE_SHORT_TIME_GAP_MINUTES = 2;
    public static final double GPS_DRIFT_DISTANCE_METERS = 20.0;
    
    // Analysis window sizes
    public static final int VELOCITY_LOOKBACK_WINDOW = 10;
    public static final int VELOCITY_LOOKFORWARD_WINDOW = 10;
    public static final int MIN_ACCURATE_POINTS_FOR_ANALYSIS = 6;
    public static final int MIN_POINTS_FOR_RELIABLE_DETECTION = 4;
    
    // Segment analysis thresholds
    public static final double SMALL_SEGMENT_RATIO = 0.1; // 10% of total points
    public static final double SUBSTANTIAL_SEGMENT_RATIO = 0.1; // 10% of total points
    public static final double ADAPTIVE_WINDOW_DIVISOR = 10.0; // points.size() / 10
    public static final int MIN_ADAPTIVE_WINDOW_SIZE = 3;
    
    // Statistical percentiles
    public static final double P95_PERCENTILE = 0.95;
    
    // Timeline processing limits
    public static final int MAX_TIMELINE_DAYS = 365; // Maximum timeline span in days

    private TimelineConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}