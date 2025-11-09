package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO containing aggregated statistics for a specific place.
 * Includes visit counts, duration statistics, and temporal information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceStatisticsDTO {

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
}
