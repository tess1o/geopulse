package org.github.tess1o.geopulse.ai.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI-optimized DTO for aggregated stay statistics.
 * Contains statistical analysis of stays grouped by various criteria.
 */
@Data
@NoArgsConstructor
public class AIStayStatsDTO {
    
    /**
     * The actual value that was grouped by (e.g., "Home", "New York", "2024-09-15")
     */
    private String groupKey;
    
    /**
     * What type of grouping this represents (e.g., "locationName", "city", "day")
     */
    private String groupType;
    
    /**
     * Number of stays in this group
     */
    private long stayCount;
    
    /**
     * Total time spent in all stays in this group (seconds)
     */
    private long totalDurationSeconds;
    
    /**
     * Average duration per stay in this group (seconds)
     */
    private double avgDurationSeconds;
    
    /**
     * Duration of the shortest stay in this group (seconds)
     */
    private long minDurationSeconds;
    
    /**
     * Duration of the longest stay in this group (seconds)
     */
    private long maxDurationSeconds;

    /**
     * Constructor for database query mapping
     */
    public AIStayStatsDTO(String groupKey, String groupType, long stayCount, 
                         long totalDurationSeconds, double avgDurationSeconds,
                         long minDurationSeconds, long maxDurationSeconds) {
        this.groupKey = groupKey;
        this.groupType = groupType;
        this.stayCount = stayCount;
        this.totalDurationSeconds = totalDurationSeconds;
        this.avgDurationSeconds = avgDurationSeconds;
        this.minDurationSeconds = minDurationSeconds;
        this.maxDurationSeconds = maxDurationSeconds;
    }
}