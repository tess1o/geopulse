package org.github.tess1o.geopulse.ai.service;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.ai.model.*;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineAggregator;
import org.github.tess1o.geopulse.statistics.service.RoutesAnalysisService;
import org.github.tess1o.geopulse.statistics.model.RoutesStatistics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Simple AI Tools class without CDI proxying to work with LangChain4j reflection
 */
@Slf4j
public class AITimelineTools {

    private final StreamingTimelineAggregator streamingTimelineAggregator;
    private final CurrentUserService currentUserService;
    private final RoutesAnalysisService routesAnalysisService;

    public AITimelineTools(StreamingTimelineAggregator streamingTimelineAggregator,
                           CurrentUserService currentUserService,
                           RoutesAnalysisService routesAnalysisService) {
        this.streamingTimelineAggregator = streamingTimelineAggregator;
        this.currentUserService = currentUserService;
        this.routesAnalysisService = routesAnalysisService;
    }

    @Tool("Gets complete timeline with all stays and trips in chronological order. Use when listing specific events or detailed activity.")
    public AIMovementTimelineDTO queryTimeline(@P("Start date (YYYY-MM-DD)") String startDate, @P("End date (YYYY-MM-DD)") String endDate) {
        log.info("🔧 AI TOOL EXECUTED: queryTimeline({}, {})", startDate, endDate);
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        AIMovementTimelineDTO timeline = getTimeline(start, end);
        log.debug("Returned AI timeline with {} stays and {} trips", timeline.getStaysCount(), timeline.getTripsCount());
        return timeline;
    }

    @Tool("Lists all places stayed at with timestamps. Use ONLY for listing specific places, NOT for counting. For counts use getStayStats.")
    public java.util.List<AITimelineStayDTO> getVisitedLocations(@P("Start date (YYYY-MM-DD)") String startDate, @P("End date (YYYY-MM-DD)") String endDate) {
        log.info("🔧 AI TOOL EXECUTED: getVisitedLocations({}, {})", startDate, endDate);
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        AIMovementTimelineDTO timeline = getTimeline(start, end);
        log.debug("Returned {} visited locations", timeline.getStaysCount());
        return timeline.getStays();
    }

    @Tool("Lists all individual trips with details. Use ONLY for listing specific trips, NOT for totals or distances. For aggregations use getTripStats.")
    public java.util.List<AITimelineTripDTO> getTripMovements(@P("Start date (YYYY-MM-DD)") String startDate, @P("End date (YYYY-MM-DD)") String endDate) {
        log.info("🔧 AI TOOL EXECUTED: getTripMovements({}, {})", startDate, endDate);
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        AIMovementTimelineDTO timeline = getTimeline(start, end);
        log.debug("Returned {} trip movements", timeline.getTripsCount());
        return timeline.getTrips();
    }

    private AIMovementTimelineDTO getTimeline(LocalDate startDate, LocalDate endDate) {
        Instant start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        UUID userId = currentUserService.getCurrentUserId();

        try {
            AIMovementTimelineDTO timeline = streamingTimelineAggregator.getTimelineForAI(userId, start, end);
            return timeline;
        } catch (Exception e) {
            log.error("Unable to query timeline", e);
        }
        return new AIMovementTimelineDTO();
    }

    @Tool("Calculates aggregated stay statistics: total time, visit counts, number of unique cities/locations/countries. Use for counting cities, comparing time spent, and statistical analysis grouped by location, city, country, day, week, or month.")
    public List<AIStayStatsDTO> getStayStats(
            @P("Start date (YYYY-MM-DD)") String startDate,
            @P("End date (YYYY-MM-DD)") String endDate,
            @P("Group by: LOCATION_NAME, CITY, COUNTRY, DAY, WEEK, or MONTH") StayGroupBy groupBy) {

        log.info("🔧 AI TOOL EXECUTED: getStayStats({}, {}, {})", startDate, endDate, groupBy);

        LocalDate startLocalDate = LocalDate.parse(startDate);
        LocalDate endLocalDate = LocalDate.parse(endDate);
        Instant start = startLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = endLocalDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        UUID userId = currentUserService.getCurrentUserId();

        try {
            List<AIStayStatsDTO> stats = streamingTimelineAggregator.getStayStats(userId, start, end, groupBy);
            log.debug("Returned {} stay statistics groups", stats);
            return stats;
        } catch (IllegalArgumentException e) {
            log.error("Invalid groupBy parameter for stay stats: {}", groupBy, e);
            // Return empty list with error info
            return List.of();
        } catch (Exception e) {
            log.error("Unable to query stay statistics", e);
            return List.of();
        }
    }

    @Tool("Calculates aggregated trip statistics: total distance, duration, trip counts by transportation mode. Use for comparing walking vs driving, analyzing travel patterns grouped by movement type, origin, destination, day, week, or month.")
    public List<AITripStatsDTO> getTripStats(
            @P("Start date (YYYY-MM-DD)") String startDate,
            @P("End date (YYYY-MM-DD)") String endDate,
            @P("Group by: MOVEMENT_TYPE, ORIGIN_LOCATION_NAME, DESTINATION_LOCATION_NAME, DAY, WEEK, or MONTH") TripGroupBy groupBy) {

        log.info("🔧 AI TOOL EXECUTED: getTripStats({}, {}, {})", startDate, endDate, groupBy);

        LocalDate startLocalDate = LocalDate.parse(startDate);
        LocalDate endLocalDate = LocalDate.parse(endDate);
        Instant start = startLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = endLocalDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        UUID userId = currentUserService.getCurrentUserId();

        try {
            List<AITripStatsDTO> stats = streamingTimelineAggregator.getTripStats(userId, start, end, groupBy);
            log.debug("Returned {} trip statistics groups", stats.size());
            return stats;
        } catch (IllegalArgumentException e) {
            log.error("Invalid groupBy parameter for trip stats: {}", groupBy, e);
            // Return empty list with error info
            return List.of();
        } catch (Exception e) {
            log.error("Unable to query trip statistics", e);
            return List.of();
        }
    }

    @Tool("Analyzes route patterns: most common routes, unique route count, average/longest trip. Use for route frequency and travel diversity. NOT for transport modes or location visits.")
    public RoutesStatistics getRoutePatterns(@P("Start date (YYYY-MM-DD)") String startDate, @P("End date (YYYY-MM-DD)") String endDate) {
        log.info("🔧 AI TOOL EXECUTED: getRoutePatterns({}, {})", startDate, endDate);

        LocalDate startLocalDate = LocalDate.parse(startDate);
        LocalDate endLocalDate = LocalDate.parse(endDate);
        Instant start = startLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = endLocalDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        UUID userId = currentUserService.getCurrentUserId();

        try {
            MovementTimelineDTO timeline = streamingTimelineAggregator.getTimelineFromDb(userId, start, end);
            RoutesStatistics routeStats = routesAnalysisService.getRoutesStatistics(timeline);
            log.debug("Returned route patterns: {} unique routes, most common: {}",
                    routeStats.getUniqueRoutesCount(),
                    routeStats.getMostCommonRoute().getName());

            return routeStats;
        } catch (Exception e) {
            log.error("Unable to analyze route patterns", e);
            // Return empty statistics on error
            return RoutesStatistics.builder()
                    .uniqueRoutesCount(0)
                    .avgTripDurationSeconds(0.0)
                    .longestTripDurationSeconds(0.0)
                    .longestTripDistanceMeters(0.0)
                    .build();
        }
    }
}