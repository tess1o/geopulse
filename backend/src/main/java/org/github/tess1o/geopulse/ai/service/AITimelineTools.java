package org.github.tess1o.geopulse.ai.service;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.ai.model.*;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineAggregator;

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

    public AITimelineTools(StreamingTimelineAggregator streamingTimelineAggregator,
                           CurrentUserService currentUserService) {
        this.streamingTimelineAggregator = streamingTimelineAggregator;
        this.currentUserService = currentUserService;
    }

    @Tool("Query user's timeline data for specific date ranges and analyze stays/trips/data gaps. Returns enriched timeline data with city/country info and trip origins/destinations. End date must be after start date at least by 1 day")
    public AIMovementTimelineDTO queryTimeline(@P("Start date") LocalDate startDate, @P("End date") LocalDate endDate) {
        log.info("🔧 AI TOOL EXECUTED: queryTimeline({}, {})", startDate, endDate);
        AIMovementTimelineDTO timeline = getTimeline(startDate, endDate);
        log.debug("Returned AI timeline with {} stays and {} trips", timeline.getStaysCount(), timeline.getTripsCount());
        return timeline;
    }

    @Tool("""
            Get the detailed list of locations/places the user visited (stays) for a specific date range.
            Use this for listing specific locations visited on particular dates, NOT for counting or comparing.
            For questions like 'which month had most cities' or 'how many cities' use getStayStats instead.
            """)
    public java.util.List<AITimelineStayDTO> getVisitedLocations(@P("Start date") LocalDate startDate, @P("End date") LocalDate endDate) {
        log.info("🔧 AI TOOL EXECUTED: getVisitedLocations({}, {})", startDate, endDate);
        AIMovementTimelineDTO timeline = getTimeline(startDate, endDate);
        log.debug("Returned {} visited locations", timeline.getStaysCount());
        return timeline.getStays();
    }

    @Tool("""
            Get only the trips/movements the user made for a specific date range with origin/destination information
            Do NOT use for totals, comparisons, or aggregations. This tool is only for listing individual trips
            """)
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
        Get comprehensive aggregated STAY statistics for analyzing location patterns and time spent at places. 
        Use this for questions about WHERE and HOW LONG the user stayed at different locations.
        ALWAYS use this for counting cities, comparing months/periods, or finding patterns.
        
        Returns enhanced statistics including:
        - Basic counts and durations (stayCount, totalDuration, avgDuration, min/max)
        - Unique counts (uniqueCityCount, uniqueLocationCount, uniqueCountryCount) 
        - Temporal info (firstStayStart for "when did I first visit X")
        - Dominant location (location with most time in each group)
        
        Perfect for questions like:
        • "How much time did I spend at home vs office last month?"
        • "Which city did I visit most this year?" 
        • "What was my longest stay at any location?"
        • "Which day/week/month did I spend most time at home?"
        • "How many different places did I visit in September?"
        • "In which month did I visit the most number of cities?" (use month groupBy, check uniqueCityCount)
        • "Which month had the most unique locations?" (use month groupBy, check uniqueLocationCount)
        • "How many cities did I visit each month?" (use month groupBy, check uniqueCityCount)
        • "When did I first visit New York?" (use city groupBy, check firstStayStart)
        • "Where do I spend most time each week?" (use week groupBy, check dominantLocation)
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
        Get aggregated TRIP statistics for analyzing travel patterns, distances, and movement types.
        Use this for questions about HOW the user traveled and trip characteristics.
        Always use this tool for totals (distance, duration, count) and comparisons.
        
        Perfect for questions like:
        • "Did I walk more or drive more in August?"
        • "How many trips did I take last week?"
        • "What was my longest trip distance/time?" 
        • "Which routes do I travel most frequently?" (use originLocationName or destinationLocationName)
        • "How far did I travel by car vs walking this month?"
        • "Which day/week/month did I travel the most?"
               
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
            log.info("Trip statistics: {}", stats);
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
}