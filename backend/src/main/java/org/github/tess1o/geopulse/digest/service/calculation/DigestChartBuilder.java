package org.github.tess1o.geopulse.digest.service.calculation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.digest.model.ActivityChartData;
import org.github.tess1o.geopulse.statistics.service.StatisticsService;
import org.github.tess1o.geopulse.statistics.model.BarChartData;
import org.github.tess1o.geopulse.statistics.model.ChartGroupMode;
import org.github.tess1o.geopulse.statistics.model.UserStatistics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds chart data for digest visualizations.
 */
@ApplicationScoped
public class DigestChartBuilder {

    @Inject
    StatisticsService statisticsService;

    /**
     * Build activity chart data from trip type charts.
     *
     * @param chartsByTripType Map of trip type to chart data
     * @return ActivityChartData with all trip types
     */
    public ActivityChartData buildActivityChartData(Map<String, BarChartData> chartsByTripType) {
        return ActivityChartData.builder()
                .chartsByTripType(chartsByTripType != null ? chartsByTripType : new HashMap<>())
                .build();
    }

    /**
     * Build monthly chart data for yearly digest.
     * Creates 12 data points (one per month) for the entire year.
     *
     * @param userId User ID
     * @param year   Year to analyze
     * @param zoneId User's timezone
     * @return ActivityChartData with monthly aggregated data for all trip types
     */
    public ActivityChartData buildMonthlyChartForYear(UUID userId, int year, ZoneId zoneId) {
        String[] monthLabels = new String[12];
        Map<String, double[]> tripTypeDistances = new HashMap<>();

        // Month names for labels
        String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                               "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

        // Fetch data for each month
        for (int month = 1; month <= 12; month++) {
            YearMonth yearMonth = YearMonth.of(year, month);
            LocalDate firstDay = yearMonth.atDay(1);
            LocalDate lastDay = yearMonth.atEndOfMonth();

            Instant start = firstDay.atStartOfDay(zoneId).toInstant();
            Instant end = lastDay.plusDays(1).atStartOfDay(zoneId).toInstant().minusNanos(1);

            // Get statistics for this month
            UserStatistics monthStats = statisticsService.getStatistics(userId, start, end, ChartGroupMode.WEEKS);

            // Store label
            monthLabels[month - 1] = monthNames[month - 1];

            // Extract distances from all trip types
            if (monthStats.getDistanceChartsByTripType() != null) {
                final int monthIndex = month - 1; // Make it effectively final for lambda
                monthStats.getDistanceChartsByTripType().forEach((tripType, chartData) -> {
                    if (chartData != null && chartData.getData() != null) {
                        // Initialize array for this trip type if not exists
                        tripTypeDistances.putIfAbsent(tripType, new double[12]);

                        // Sum up all distances for this month (charts are in km)
                        tripTypeDistances.get(tripType)[monthIndex] =
                            Arrays.stream(chartData.getData()).sum();
                    }
                });
            }
        }

        // Build map of trip type to BarChartData
        Map<String, BarChartData> chartsByTripType = new HashMap<>();
        tripTypeDistances.forEach((tripType, distances) -> {
            chartsByTripType.put(tripType, new BarChartData(monthLabels, distances));
        });

        return ActivityChartData.builder()
                .chartsByTripType(chartsByTripType)
                .build();
    }
}
