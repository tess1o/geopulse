package org.github.tess1o.geopulse.digest.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.digest.model.*;
import org.github.tess1o.geopulse.digest.service.calculation.*;
import org.github.tess1o.geopulse.digest.service.milestone.MilestoneEvaluator;
import org.github.tess1o.geopulse.digest.service.util.PeriodInfoBuilder;
import org.github.tess1o.geopulse.statistics.model.ChartGroupMode;
import org.github.tess1o.geopulse.statistics.model.HeatmapPlace;
import org.github.tess1o.geopulse.statistics.model.UserStatistics;
import org.github.tess1o.geopulse.statistics.repository.StatisticsRepository;
import org.github.tess1o.geopulse.statistics.service.StatisticsService;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineAggregator;

import java.time.*;
import java.util.*;

/**
 * Orchestrates digest generation by delegating to specialized service classes.
 * Refactored from 981 lines to ~120 lines by extracting focused
 * responsibilities.
 */
@ApplicationScoped
@Slf4j
public class DigestServiceImpl implements DigestService {

    private static final int TRIP_HEATMAP_GRID_METERS = 75;
    private static final int TRIP_HEATMAP_MAX_GAP_SECONDS = 300;

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

    @Inject
    StatisticsRepository statisticsRepository;

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
        UserStatistics previousStats = statisticsService.getStatistics(userId, prevStart, prevEnd,
                ChartGroupMode.WEEKS);

        // Build digest using specialized services
        return TimeDigest.builder()
                .period(periodInfoBuilder.buildPeriodInfo(year, month))
                .metrics(metricsCalculator.buildMetrics(userId, currentStats, timeline, start, end, zoneId))
                .comparison(comparisonCalculator.buildComparison(currentStats, previousStats))
                .highlights(highlightsAnalyzer.buildHighlights(currentStats, timeline, start, end, zoneId))
                .topPlaces(currentStats.getPlaces() != null ? currentStats.getPlaces() : List.of())
                .activityChart(chartBuilder.buildActivityChartData(currentStats.getDistanceChartsByTripType()))
                .milestones(milestoneEvaluator.buildMilestones(userId, currentStats, timeline, "monthly", start, end,
                        zoneId))
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
        UserStatistics previousStats = statisticsService.getStatistics(userId, prevStart, prevEnd,
                ChartGroupMode.WEEKS);

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
                .milestones(milestoneEvaluator.buildMilestones(userId, currentStats, timeline, "yearly", start, end,
                        zoneId))
                .build();
    }

    @Override
    public List<HeatmapDataPoint> getMonthlyHeatmap(UUID userId, int year, int month, String timezone) {
        return getMonthlyHeatmap(userId, year, month, timezone, HeatmapLayer.COMBINED);
    }

    @Override
    public List<HeatmapDataPoint> getMonthlyHeatmap(UUID userId, int year, int month, String timezone, HeatmapLayer layer) {
        log.info("Generating monthly heatmap for user {} - {}/{}", userId, year, month);
        ZoneId zoneId = ZoneId.of(timezone);
        YearMonth yearMonth = YearMonth.of(year, month);
        Instant start = yearMonth.atDay(1).atStartOfDay(zoneId).toInstant();
        Instant end = yearMonth.atEndOfMonth().plusDays(1).atStartOfDay(zoneId).toInstant().minusNanos(1);
        return buildHeatmapPoints(userId, start, end, layer);
    }

    @Override
    public List<HeatmapDataPoint> getYearlyHeatmap(UUID userId, int year, String timezone) {
        return getYearlyHeatmap(userId, year, timezone, HeatmapLayer.COMBINED);
    }

    @Override
    public List<HeatmapDataPoint> getYearlyHeatmap(UUID userId, int year, String timezone, HeatmapLayer layer) {
        log.info("Generating yearly heatmap for user {} - {}", userId, year);
        ZoneId zoneId = ZoneId.of(timezone);
        Instant start = LocalDate.of(year, 1, 1).atStartOfDay(zoneId).toInstant();
        Instant end = LocalDate.of(year, 12, 31).plusDays(1).atStartOfDay(zoneId).toInstant().minusNanos(1);
        return buildHeatmapPoints(userId, start, end, layer);
    }

    @Override
    public List<HeatmapDataPoint> getHeatmapForRange(UUID userId, Instant start, Instant end, HeatmapLayer layer) {
        log.info("Generating range heatmap for user {} - {} to {}", userId, start, end);
        return buildHeatmapPoints(userId, start, end, layer);
    }

    private List<HeatmapDataPoint> buildHeatmapPoints(UUID userId, Instant start, Instant end, HeatmapLayer layer) {
        java.util.stream.Stream<HeatmapPlace> stream;
        if (layer == HeatmapLayer.STAYS) {
            stream = statisticsRepository.getHeatmapPlaces(userId, start, end).stream();
        } else if (layer == HeatmapLayer.TRIPS) {
            stream = statisticsRepository.getTripHeatmapPlaces(
                    userId,
                    start,
                    end,
                    TRIP_HEATMAP_GRID_METERS,
                    TRIP_HEATMAP_MAX_GAP_SECONDS
            ).stream();
        } else {
            List<HeatmapPlace> stayPlaces = statisticsRepository.getHeatmapPlaces(userId, start, end);
            List<HeatmapPlace> tripPlaces = statisticsRepository.getTripHeatmapPlaces(
                    userId,
                    start,
                    end,
                    TRIP_HEATMAP_GRID_METERS,
                    TRIP_HEATMAP_MAX_GAP_SECONDS
            );
            stream = java.util.stream.Stream.concat(stayPlaces.stream(), tripPlaces.stream());
        }

        return stream
                .filter(p -> p.getLatitude() != 0 || p.getLongitude() != 0)
                .map(p -> HeatmapDataPoint.builder()
                        .lat(p.getLatitude())
                        .lng(p.getLongitude())
                        .durationSeconds(p.getDurationSeconds())
                        .visits(p.getVisits())
                        .name(p.getName())
                        .build())
                .toList();
    }
}
