package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO containing aggregated statistics for a city or country.
 * Similar to PlaceStatisticsDTO but includes location-specific metrics.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationStatisticsDTO {

    // Visit counts
    private long totalVisits;
    private long visitsThisWeek;
    private long visitsThisMonth;
    private long visitsThisYear;

    // Duration statistics (in seconds)
    private long totalDuration;
    private long averageDuration;
    private long minDuration;
    private long maxDuration;

    // Temporal information
    private Instant firstVisit;
    private Instant lastVisit;

    // Location-specific metrics
    private int uniquePlaces;           // Number of distinct places in this location
    private long totalDistanceTraveled;  // Total distance traveled (if available from trips)
}
