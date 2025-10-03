package org.github.tess1o.geopulse.digest.service.calculation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.digest.model.ActivityChartData;
import org.github.tess1o.geopulse.statistics.StatisticsService;
import org.github.tess1o.geopulse.statistics.model.BarChartData;
import org.github.tess1o.geopulse.statistics.model.ChartGroupMode;
import org.github.tess1o.geopulse.statistics.model.UserStatistics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Builds chart data for digest visualizations.
 */
@ApplicationScoped
public class DigestChartBuilder {

    @Inject
    StatisticsService statisticsService;

    /**
     * Build activity chart data from existing car and walk charts.
     *
     * @param carChart  Car distance chart
     * @param walkChart Walk distance chart
     * @return ActivityChartData with separate car and walk datasets
     */
    public ActivityChartData buildActivityChartData(BarChartData carChart, BarChartData walkChart) {
        return ActivityChartData.builder()
                .carChart(carChart)
                .walkChart(walkChart)
                .build();
    }

    /**
     * Build monthly chart data for yearly digest.
     * Creates 12 data points (one per month) for the entire year.
     *
     * @param userId User ID
     * @param year   Year to analyze
     * @param zoneId User's timezone
     * @return ActivityChartData with monthly aggregated data
     */
    public ActivityChartData buildMonthlyChartForYear(UUID userId, int year, ZoneId zoneId) {
        String[] monthLabels = new String[12];
        double[] carDistances = new double[12];
        double[] walkDistances = new double[12];

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

            // Extract car and walk distances from the month's charts
            if (monthStats.getDistanceCarChart() != null && monthStats.getDistanceCarChart().getData() != null) {
                // Sum up all car distances for this month (charts are in km)
                carDistances[month - 1] = java.util.Arrays.stream(monthStats.getDistanceCarChart().getData()).sum();
            }

            if (monthStats.getDistanceWalkChart() != null && monthStats.getDistanceWalkChart().getData() != null) {
                // Sum up all walk distances for this month (charts are in km)
                walkDistances[month - 1] = java.util.Arrays.stream(monthStats.getDistanceWalkChart().getData()).sum();
            }
        }

        return ActivityChartData.builder()
                .carChart(new BarChartData(monthLabels, carDistances))
                .walkChart(new BarChartData(monthLabels, walkDistances))
                .build();
    }
}
