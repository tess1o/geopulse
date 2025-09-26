package org.github.tess1o.geopulse.ai.model;

import lombok.Builder;
import lombok.Data;

/**
 * AI-optimized DTO for aggregated stay statistics.
 * Contains statistical analysis of stays grouped by various criteria.
 */
@Data
@Builder
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
     * Number of unique cities in this group
     */
    private long uniqueCityCount;
    
    /**
     * Number of unique location names in this group
     */
    private long uniqueLocationCount;
    
    /**
     * Number of unique countries in this group
     */
    private long uniqueCountryCount;
    
    /**
     * Timestamp of the earliest stay in this group
     */
    private java.time.Instant firstStayStart;
    
    /**
     * Location name that had the most total time in this group
     */
    private String dominantLocation;

}