package org.github.tess1o.geopulse.ai.model;

import lombok.Builder;
import lombok.Data;

/**
 * AI-optimized DTO for aggregated trip statistics.
 * Contains statistical analysis of trips grouped by various criteria including distance, duration, and performance metrics.
 */
@Data
@Builder
public class AITripStatsDTO {
    
    /**
     * The actual value that was grouped by (e.g., "WALKING", "Home->Office", "2024-09-15")
     */
    private String groupKey;
    
    /**
     * What type of grouping this represents (e.g., "movementType", "originLocationName", "day")
     */
    private String groupType;
    
    /**
     * Number of trips in this group
     */
    private long tripCount;
    
    /**
     * Total distance traveled in all trips in this group (meters)
     */
    private long totalDistanceMeters;
    
    /**
     * Average distance per trip in this group (meters)
     */
    private double avgDistanceMeters;
    
    /**
     * Distance of the shortest trip in this group (meters)
     */
    private long minDistanceMeters;
    
    /**
     * Distance of the longest trip in this group (meters)
     */
    private long maxDistanceMeters;
    
    /**
     * Total duration of all trips in this group (seconds)
     */
    private long totalDurationSeconds;
    
    /**
     * Average duration per trip in this group (seconds)
     */
    private double avgDurationSeconds;
    
    /**
     * Duration of the shortest trip in this group (seconds)
     */
    private long minDurationSeconds;
    
    /**
     * Duration of the longest trip in this group (seconds)
     */
    private long maxDurationSeconds;
    
    /**
     * Average speed across all trips in this group (km/h)
     * Calculated from total distance and total duration
     */
    private double avgSpeedKmh;
}