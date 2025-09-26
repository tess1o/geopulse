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

    @Tool("Retrieves a user's complete timeline for a date range, including all stays, trips, and data gaps. Use for detailed, chronological views of user activity.")
    public AIMovementTimelineDTO queryTimeline(@P("Start date") LocalDate startDate, @P("End date") LocalDate endDate) {
        log.info("🔧 AI TOOL EXECUTED: queryTimeline({}, {})", startDate, endDate);
        AIMovementTimelineDTO timeline = getTimeline(startDate, endDate);
        log.debug("Returned AI timeline with {} stays and {} trips", timeline.getStaysCount(), timeline.getTripsCount());
        return timeline;
    }

    @Tool("Lists all locations/places a user has stayed at within a date range. Use this ONLY for listing specific places. For counting or statistical analysis, use getStayStats.")
    public java.util.List<AITimelineStayDTO> getVisitedLocations(@P("Start date") LocalDate startDate, @P("End date") LocalDate endDate) {
        log.info("🔧 AI TOOL EXECUTED: getVisitedLocations({}, {})", startDate, endDate);
        AIMovementTimelineDTO timeline = getTimeline(startDate, endDate);
        log.debug("Returned {} visited locations", timeline.getStaysCount());
        return timeline.getStays();
    }

    @Tool("Lists all trips/movements a user made within a date range. Use this ONLY for listing individual trips. For totals, distances, or other aggregations, use getTripStats.")
    public java.util.List<AITimelineTripDTO> getTripMovements(@P("Start date") LocalDate startDate, @P("End date") LocalDate endDate) {
        log.info("🔧 AI TOOL EXECUTED: getTripMovements({}, {})", startDate, endDate);
        AIMovementTimelineDTO timeline = getTimeline(startDate, endDate);
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

    @Tool("""
            Calculates comprehensive aggregated statistics about user stays. Use for questions about time spent at locations, 
            visit frequency, and unique counts of cities or places. Returns enhanced data including unique counts, temporal info, and dominant locations.
            
            Enhanced fields returned:
            - uniqueCityCount: Number of distinct cities in each group
            - uniqueLocationCount: Number of distinct locations in each group  
            - uniqueCountryCount: Number of distinct countries in each group
            - firstStayStart: Earliest stay timestamp in each group
            - dominantLocation: Location with most time in each group
            
            Grouping examples:
            - LOCATION_NAME: "How much time at each location?" "Which location did I visit most?"
            - CITY: "How much time in each city?" "Which city did I visit most frequently?" "What locations did I visit in Boston?"
            - COUNTRY: "How much time per country?" "Which country has most unique cities?"
            - MONTH: "Which month had most cities?" "How many unique locations each month?"
            - WEEK: "Where do I spend most time each week?" "Which week had most travel diversity?"
            - DAY: "Which day had most unique locations?" "How many cities visited per day?"
            
            Perfect for: counting, comparisons, patterns, "how much/many", "which most/least", statistical analysis.
            """)
    public List<AIStayStatsDTO> getStayStats(
            @P("Start date") LocalDate startDate,
            @P("End date") LocalDate endDate,
            @P("Group stays by: locationName, city, country, day, week, month") StayGroupBy groupBy) {

        log.info("🔧 AI TOOL EXECUTED: getStayStats({}, {}, {})", startDate, endDate, groupBy);

        Instant start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
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

    @Tool("""
            Calculates aggregated statistics about user trips. Use for questions about travel patterns, distances, and movement types. Ideal for answering questions about total distance, duration, trip counts, and transportation modes.
            
            Grouping examples:
            - movementType: "Did I walk more or drive more?" "How far did I travel by car vs. walking?"
            - originLocationName: "Which routes do I travel most frequently?"
            - destinationLocationName: "Which are my most common destinations?"
            - day/week/month: "Which day had the most travel?" "How many trips per month?"
            """)
    public List<AITripStatsDTO> getTripStats(
            @P("Start date") LocalDate startDate,
            @P("End date") LocalDate endDate,
            @P("Group trips by: movementType, originLocationName, destinationLocationName, day, week, month") TripGroupBy groupBy) {

        log.info("🔧 AI TOOL EXECUTED: getTripStats({}, {}, {})", startDate, endDate, groupBy);

        Instant start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
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

    @Tool("""
            Analyzes route patterns and travel behavior. Use for questions about most common routes, route frequency, and travel patterns.
            
            Perfect for answering:
            - "What's my most common route?" "Which route do I take most frequently?"
            - "How many unique routes do I have?" "How diverse are my travel patterns?"
            - "What's my longest trip?" "What's my average trip duration?"
            - Route efficiency and travel pattern analysis
            
            NOT for: transportation modes (use getTripStats), location visits (use getStayStats), or distance by transport type.
            """)
    public RoutesStatistics getRoutePatterns(@P("Start date") LocalDate startDate, @P("End date") LocalDate endDate) {
        log.info("🔧 AI TOOL EXECUTED: getRoutePatterns({}, {})", startDate, endDate);

        Instant start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
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