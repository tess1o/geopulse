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

        // Calculate distances by trip type from charts
        double carDistance = calculateDistanceForTripType(stats, "CAR");
        double walkDistance = calculateDistanceForTripType(stats, "WALK");
        double bicycleDistance = calculateDistanceForTripType(stats, "BICYCLE");
        double runningDistance = calculateDistanceForTripType(stats, "RUNNING");
        double trainDistance = calculateDistanceForTripType(stats, "TRAIN");
        double flightDistance = calculateDistanceForTripType(stats, "FLIGHT");
        double unknownDistance = calculateDistanceForTripType(stats, "UNKNOWN");

        return DigestMetrics.builder()
                .totalDistance(stats.getTotalDistanceMeters())
                .activeDays(activeDaysCount)
                .citiesVisited(citiesCount)
                .tripCount(timeline.getTripsCount())
                .stayCount(timeline.getStaysCount())
                // Distance by trip type (charts are in km, convert to meters)
                .carDistance(carDistance * 1000)
                .walkDistance(walkDistance * 1000)
                .bicycleDistance(bicycleDistance * 1000)
                .runningDistance(runningDistance * 1000)
                .trainDistance(trainDistance * 1000)
                .flightDistance(flightDistance * 1000)
                .unknownDistance(unknownDistance * 1000)
                // Other enhanced metrics
                .timeMoving(stats.getTimeMoving())
                .dailyAverageDistance(stats.getDailyAverageDistanceMeters())
                .build();
    }

    /**
     * Calculate distance for a specific trip type from statistics charts.
     *
     * @param stats    User statistics containing distance charts
     * @param tripType Trip type name (e.g., "CAR", "WALK", "BICYCLE")
     * @return Distance in kilometers (0 if no data)
     */
    private double calculateDistanceForTripType(UserStatistics stats, String tripType) {
        if (stats.getDistanceChartsByTripType() == null) {
            return 0;
        }
        var chartData = stats.getDistanceChartsByTripType().get(tripType);
        if (chartData == null || chartData.getData() == null) {
            return 0;
        }
        return java.util.Arrays.stream(chartData.getData()).sum();
    }
}
