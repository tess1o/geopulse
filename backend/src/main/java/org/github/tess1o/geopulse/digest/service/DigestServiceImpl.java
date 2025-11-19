package org.github.tess1o.geopulse.digest.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.digest.model.*;
import org.github.tess1o.geopulse.digest.service.calculation.*;
import org.github.tess1o.geopulse.digest.service.milestone.MilestoneEvaluator;
import org.github.tess1o.geopulse.digest.service.util.PeriodInfoBuilder;
import org.github.tess1o.geopulse.statistics.service.StatisticsService;
import org.github.tess1o.geopulse.statistics.model.ChartGroupMode;
import org.github.tess1o.geopulse.statistics.model.UserStatistics;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineAggregator;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;

import java.time.*;
import java.util.*;

/**
 * Orchestrates digest generation by delegating to specialized service classes.
 * Refactored from 981 lines to ~120 lines by extracting focused responsibilities.
 */
@ApplicationScoped
@Slf4j
public class DigestServiceImpl implements DigestService {

    @Inject
    StatisticsService statisticsService;

    @Inject
    StreamingTimelineAggregator streamingTimelineAggregator;

    @Inject
    PeriodInfoBuilder periodInfoBuilder;

    @Inject
    DigestMetricsCalculator metricsCalculator;

    @Inject
    DigestComparisonCalculator comparisonCalculator;

    @Inject
    DigestHighlightsAnalyzer highlightsAnalyzer;

    @Inject
    DigestChartBuilder chartBuilder;

    @Inject
    MilestoneEvaluator milestoneEvaluator;

    @Override
    public TimeDigest getMonthlyDigest(UUID userId, int year, int month, String timezone) {
        log.info("Generating monthly digest for user {} - {}/{}", userId, year, month);

        // Calculate time range for the month using user's timezone
        ZoneId zoneId = ZoneId.of(timezone);
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate firstDay = yearMonth.atDay(1);
        LocalDate lastDay = yearMonth.atEndOfMonth();

        Instant start = firstDay.atStartOfDay(zoneId).toInstant();
        Instant end = lastDay.plusDays(1).atStartOfDay(zoneId).toInstant().minusNanos(1);

        // Get current period statistics - use WEEKS for better chart readability
        UserStatistics currentStats = statisticsService.getStatistics(userId, start, end, ChartGroupMode.WEEKS);
        MovementTimelineDTO timeline = streamingTimelineAggregator.getTimelineFromDb(userId, start, end);

        // Get previous month for comparison
        YearMonth previousMonth = yearMonth.minusMonths(1);
        Instant prevStart = previousMonth.atDay(1).atStartOfDay(zoneId).toInstant();
        Instant prevEnd = previousMonth.atEndOfMonth().plusDays(1).atStartOfDay(zoneId).toInstant().minusNanos(1);
        UserStatistics previousStats = statisticsService.getStatistics(userId, prevStart, prevEnd, ChartGroupMode.WEEKS);

        // Build digest using specialized services
        return TimeDigest.builder()
                .period(periodInfoBuilder.buildPeriodInfo(year, month))
                .metrics(metricsCalculator.buildMetrics(userId, currentStats, timeline, start, end, zoneId))
                .comparison(comparisonCalculator.buildComparison(currentStats, previousStats))
                .highlights(highlightsAnalyzer.buildHighlights(currentStats, timeline, start, end, zoneId))
                .topPlaces(currentStats.getPlaces() != null ? currentStats.getPlaces() : List.of())
                .activityChart(chartBuilder.buildActivityChartData(currentStats.getDistanceChartsByTripType()))
                .milestones(milestoneEvaluator.buildMilestones(userId, currentStats, timeline, "monthly", start, end, zoneId))
                .build();
    }

    @Override
    public TimeDigest getYearlyDigest(UUID userId, int year, String timezone) {
        log.info("Generating yearly digest for user {} - {}", userId, year);

        // Calculate time range for the year using user's timezone
        ZoneId zoneId = ZoneId.of(timezone);
        LocalDate firstDay = LocalDate.of(year, 1, 1);
        LocalDate lastDay = LocalDate.of(year, 12, 31);

        Instant start = firstDay.atStartOfDay(zoneId).toInstant();
        Instant end = lastDay.plusDays(1).atStartOfDay(zoneId).toInstant().minusNanos(1);

        // Get current year statistics for overall metrics
        UserStatistics currentStats = statisticsService.getStatistics(userId, start, end, ChartGroupMode.WEEKS);
        MovementTimelineDTO timeline = streamingTimelineAggregator.getTimelineFromDb(userId, start, end);

        // Get previous year for comparison
        Instant prevStart = firstDay.minusYears(1).atStartOfDay(zoneId).toInstant();
        Instant prevEnd = lastDay.minusYears(1).plusDays(1).atStartOfDay(zoneId).toInstant().minusNanos(1);
        UserStatistics previousStats = statisticsService.getStatistics(userId, prevStart, prevEnd, ChartGroupMode.WEEKS);

        // Generate monthly chart data for yearly view (12 bars)
        ActivityChartData monthlyChart = chartBuilder.buildMonthlyChartForYear(userId, year, zoneId);

        // Build digest using specialized services
        return TimeDigest.builder()
                .period(periodInfoBuilder.buildPeriodInfo(year, null))
                .metrics(metricsCalculator.buildMetrics(userId, currentStats, timeline, start, end, zoneId))
                .comparison(comparisonCalculator.buildComparison(currentStats, previousStats))
                .highlights(highlightsAnalyzer.buildHighlights(currentStats, timeline, start, end, zoneId))
                .topPlaces(currentStats.getPlaces() != null ? currentStats.getPlaces() : List.of())
                .activityChart(monthlyChart)
                .milestones(milestoneEvaluator.buildMilestones(userId, currentStats, timeline, "yearly", start, end, zoneId))
                .build();
    }
}
