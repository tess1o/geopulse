package org.github.tess1o.geopulse.statistics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.statistics.model.ChartGroupMode;
import org.github.tess1o.geopulse.statistics.model.UserStatistics;
import org.github.tess1o.geopulse.statistics.service.ActivityAnalysisService;
import org.github.tess1o.geopulse.statistics.service.ChartDataService;
import org.github.tess1o.geopulse.statistics.service.PlacesAnalysisService;
import org.github.tess1o.geopulse.statistics.service.RoutesAnalysisService;
import org.github.tess1o.geopulse.statistics.service.TimelineAggregationService;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineAggregator;

import java.time.Instant;
import java.util.UUID;

/**
 * Main statistics service implementation that orchestrates various specialized services
 * to generate comprehensive user statistics from timeline data.
 * 
 * This service has been refactored to delegate specific responsibilities to focused services:
 * - Chart generation
 * - Places analysis
 * - Routes analysis
 * - Timeline aggregation
 * - Activity analysis
 */
@ApplicationScoped
@Slf4j
public class StatisticsServiceImpl implements StatisticsService {

    private final StreamingTimelineAggregator streamingTimelineAggregator;
    private final ChartDataService chartDataService;
    private final PlacesAnalysisService placesAnalysisService;
    private final RoutesAnalysisService routesAnalysisService;
    private final TimelineAggregationService timelineAggregationService;
    private final ActivityAnalysisService activityAnalysisService;

    @Inject
    public StatisticsServiceImpl(
            StreamingTimelineAggregator streamingTimelineAggregator,
            ChartDataService chartDataService,
            PlacesAnalysisService placesAnalysisService,
            RoutesAnalysisService routesAnalysisService,
            TimelineAggregationService timelineAggregationService,
            ActivityAnalysisService activityAnalysisService) {
        this.streamingTimelineAggregator = streamingTimelineAggregator;
        this.chartDataService = chartDataService;
        this.placesAnalysisService = placesAnalysisService;
        this.routesAnalysisService = routesAnalysisService;
        this.timelineAggregationService = timelineAggregationService;
        this.activityAnalysisService = activityAnalysisService;
    }

    @Override
    public UserStatistics getStatistics(UUID userId, Instant from, Instant to, ChartGroupMode chartGroupMode) {
        log.debug("Generating statistics for user {} from {} to {} with grouping {}", 
                userId, from, to, chartGroupMode);
        
        MovementTimelineDTO timeline = streamingTimelineAggregator.getTimelineFromDb(userId, from, to);
        
        // Calculate basic aggregations
        double totalDistanceKm = timelineAggregationService.getTotalDistance(timeline);
        long timeMovingMinutes = timelineAggregationService.getTimeMoving(timeline);
        
        // Build complete statistics using specialized services
        return UserStatistics.builder()
                .totalDistance(totalDistanceKm)
                .timeMoving(timeMovingMinutes)
                .dailyAverage(timelineAggregationService.getDailyAverage(timeline))
                .uniqueLocationsCount(placesAnalysisService.getUniqueLocationsCount(timeline))
                .routes(routesAnalysisService.getRoutesStatistics(timeline))
                .places(placesAnalysisService.getPlacesStatistics(timeline))
                .mostActiveDay(activityAnalysisService.getMostActiveDay(timeline))
                .averageSpeed(timelineAggregationService.getAverageSpeed(totalDistanceKm, timeMovingMinutes))
                .distanceCarChart(chartDataService.getDistanceChartData(timeline, TripType.CAR, chartGroupMode))
                .distanceWalkChart(chartDataService.getDistanceChartData(timeline, TripType.WALK, chartGroupMode))
                .build();
    }
}