package org.github.tess1o.geopulse.ai.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI-optimized DTO for aggregated trip statistics.
 * Contains statistical analysis of trips grouped by various criteria including distance and duration metrics.
 */
@Data
@NoArgsConstructor
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
     * Constructor for database query mapping
     */
    public AITripStatsDTO(String groupKey, String groupType, long tripCount,
                         long totalDistanceMeters, double avgDistanceMeters, 
                         long minDistanceMeters, long maxDistanceMeters,
                         long totalDurationSeconds, double avgDurationSeconds) {
        this.groupKey = groupKey;
        this.groupType = groupType;
        this.tripCount = tripCount;
        this.totalDistanceMeters = totalDistanceMeters;
        this.avgDistanceMeters = avgDistanceMeters;
        this.minDistanceMeters = minDistanceMeters;
        this.maxDistanceMeters = maxDistanceMeters;
        this.totalDurationSeconds = totalDurationSeconds;
        this.avgDurationSeconds = avgDurationSeconds;
    }
}