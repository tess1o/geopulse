package org.github.tess1o.geopulse.statistics.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.statistics.model.*;
import org.github.tess1o.geopulse.statistics.repository.StatisticsRepository;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Main statistics service implementation using SQL-based aggregations.
 * All calculations are performed in the database for optimal memory efficiency.
 *
 * This eliminates the need to load timeline data into memory, reducing memory usage by 95%+.
 */
@ApplicationScoped
@Slf4j
public class StatisticsServiceImpl implements StatisticsService {

    private final StatisticsRepository statisticsRepository;

    @Inject
    public StatisticsServiceImpl(StatisticsRepository statisticsRepository) {
        this.statisticsRepository = statisticsRepository;
    }

    @Override
    public UserStatistics getStatistics(UUID userId, Instant from, Instant to, ChartGroupMode chartGroupMode) {
        log.debug("Generating statistics for user {} from {} to {} with grouping {}",
                userId, from, to, chartGroupMode);

        // Get trip aggregations
        TripAggregationResult tripAggregations = statisticsRepository.getTripAggregations(userId, from, to);

        // Calculate average speed
        double averageSpeed = calculateAverageSpeed(
                tripAggregations.getTotalDistanceMeters(),
                tripAggregations.getTotalDurationSeconds()
        );

        // Get chart data based on grouping mode
        boolean useWeeks = Duration.between(from, to).toDays() >= 10 || chartGroupMode == ChartGroupMode.WEEKS;

        // Get chart data for all trip types (excluding UNKNOWN)
        Map<String, BarChartData> chartsByTripType = Arrays.stream(TripType.values())
                .filter(tripType -> tripType != TripType.UNKNOWN) // Exclude UNKNOWN type
                .collect(Collectors.toMap(
                        TripType::name,
                        tripType -> getBarChartData(userId, from, to, tripType.name(), useWeeks),
                        (existing, replacement) -> existing, // In case of duplicates, keep existing
                        HashMap::new
                ));

        // Build complete statistics
        return UserStatistics.builder()
                .totalDistanceMeters(tripAggregations.getTotalDistanceMeters())
                .timeMoving(tripAggregations.getTotalDurationSeconds())
                .dailyAverageDistanceMeters(tripAggregations.getDailyAverageDistanceMeters())
                .uniqueLocationsCount(statisticsRepository.getUniqueLocationsCount(userId, from, to))
                .routes(statisticsRepository.getRoutesStatistics(userId, from, to))
                .places(statisticsRepository.getTopPlaces(userId, from, to, 5))
                .mostActiveDay(statisticsRepository.getMostActiveDay(userId, from, to))
                .averageSpeed(averageSpeed)
                .distanceChartsByTripType(chartsByTripType)
                .build();
    }

    /**
     * Calculate average speed from total distance and time.
     */
    private double calculateAverageSpeed(double totalDistanceMeters, long timeMovingSeconds) {
        if (timeMovingSeconds <= 0) {
            return 0.0;
        }
        double metersPerSecond = totalDistanceMeters / timeMovingSeconds;
        return metersPerSecond * 3.6; // convert m/s to km/h
    }

    /**
     * Get bar chart data from SQL results.
     */
    private BarChartData getBarChartData(UUID userId, Instant from, Instant to, String movementType, boolean useWeeks) {
        List<ChartDataPoint> dataPoints = useWeeks
                ? statisticsRepository.getChartDataByWeeks(userId, from, to, movementType)
                : statisticsRepository.getChartDataByDays(userId, from, to, movementType);

        String[] labels = dataPoints.stream()
                .map(ChartDataPoint::getLabel)
                .toArray(String[]::new);

        double[] distances = dataPoints.stream()
                .mapToDouble(ChartDataPoint::getDistanceKm)
                .toArray();

        return new BarChartData(labels, distances);
    }
}