package org.github.tess1o.geopulse.digest.service.calculation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.digest.model.DigestMetrics;
import org.github.tess1o.geopulse.digest.service.repository.DigestDataRepository;
import org.github.tess1o.geopulse.statistics.model.UserStatistics;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Calculates digest metrics from statistics and timeline data.
 */
@ApplicationScoped
public class DigestMetricsCalculator {

    @Inject
    DigestDataRepository dataRepository;

    @Inject
    ActiveDaysCalculator activeDaysCalculator;

    /**
     * Build metrics for digest display.
     *
     * @param userId   User ID
     * @param stats    User statistics for the period
     * @param timeline Timeline data
     * @param start    Period start time
     * @param end      Period end time
     * @param zoneId   User's timezone
     * @return DigestMetrics with all calculated values
     */
    public DigestMetrics buildMetrics(UUID userId, UserStatistics stats, MovementTimelineDTO timeline,
                                      Instant start, Instant end, ZoneId zoneId) {
        // Calculate active days using the dedicated calculator
        int activeDaysCount = activeDaysCalculator.calculateActiveDaysCount(timeline, start, end, zoneId);

        // Calculate unique cities from database (proper city field, not location names)
        int citiesCount = dataRepository.getUniqueCitiesCount(userId, start, end);

        // Calculate car and walk distances from charts
        double carDistance = 0;
        double walkDistance = 0;

        if (stats.getDistanceCarChart() != null && stats.getDistanceCarChart().getData() != null) {
            carDistance = java.util.Arrays.stream(stats.getDistanceCarChart().getData()).sum();
        }

        if (stats.getDistanceWalkChart() != null && stats.getDistanceWalkChart().getData() != null) {
            walkDistance = java.util.Arrays.stream(stats.getDistanceWalkChart().getData()).sum();
        }

        return DigestMetrics.builder()
                .totalDistance(stats.getTotalDistanceMeters())
                .activeDays(activeDaysCount)
                .citiesVisited(citiesCount)
                .tripCount(timeline.getTripsCount())
                .stayCount(timeline.getStaysCount())
                // Enhanced metrics
                .carDistance(carDistance * 1000) // charts are in km, convert to meters
                .walkDistance(walkDistance * 1000) // charts are in km, convert to meters
                .timeMoving(stats.getTimeMoving())
                .dailyAverageDistance(stats.getDailyAverageDistanceMeters())
                .build();
    }
}
