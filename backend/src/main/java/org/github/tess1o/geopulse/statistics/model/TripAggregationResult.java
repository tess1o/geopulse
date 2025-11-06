package org.github.tess1o.geopulse.statistics.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for trip aggregation results from SQL queries.
 * Contains basic statistics about trips: distance, duration, and daily averages.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripAggregationResult {
    private Double totalDistanceMeters;
    private Long totalDurationSeconds;
    private Double dailyAverageDistanceMeters;
    private Long daysWithTrips;
}
