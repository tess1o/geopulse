package org.github.tess1o.geopulse.ai.service;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.ai.model.AIMovementTimelineDTO;
import org.github.tess1o.geopulse.ai.model.AITimelineStayDTO;
import org.github.tess1o.geopulse.ai.model.AITimelineTripDTO;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineAggregator;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

    @Tool("Get only the locations/places the user visited (stays) for a specific date range with enriched city/country information")
    public java.util.List<AITimelineStayDTO> getVisitedLocations(@P("Start date") LocalDate startDate, @P("End date") LocalDate endDate) {
        log.info("🔧 AI TOOL EXECUTED: getVisitedLocations({}, {})", startDate, endDate);
        AIMovementTimelineDTO timeline = getTimeline(startDate, endDate);
        log.debug("Returned {} visited locations", timeline.getStaysCount());
        return timeline.getStays();
    }

    @Tool("Get only the trips/movements the user made for a specific date range with origin/destination information")
    public java.util.List<AITimelineTripDTO> getTripMovements(@P("Start date") LocalDate startDate, @P("End date") LocalDate endDate) {
        log.info("🔧 AI TOOL EXECUTED: getTripMovements({}, {})", startDate, endDate);
        AIMovementTimelineDTO timeline = getTimeline(startDate, endDate);
        log.debug("Returned {} trip movements", timeline.getTripsCount());
        return timeline.getTrips();
    }

    private AIMovementTimelineDTO getTimeline(LocalDate startDate, LocalDate endDate) {
        if (startDate.equals(endDate)) {
            endDate = endDate.plusDays(1);
        }

        Instant start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = endDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        UUID userId = currentUserService.getCurrentUserId();

        try {
            AIMovementTimelineDTO timeline = streamingTimelineAggregator.getTimelineForAI(userId, start, end);
            return timeline;
        } catch (Exception e) {
            log.error("Unable to query timeline", e);
        }
        return new AIMovementTimelineDTO();
    }
}