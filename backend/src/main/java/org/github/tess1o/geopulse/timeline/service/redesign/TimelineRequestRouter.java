package org.github.tess1o.geopulse.timeline.service.redesign;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineDataSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Main entry point for timeline requests. 
 * Classifies requests and routes to appropriate handlers.
 */
@ApplicationScoped
@Slf4j
public class TimelineRequestRouter {

    @Inject
    PastRequestHandler pastRequestHandler;
    
    @Inject
    MixedRequestHandler mixedRequestHandler;

    /**
     * Main timeline request method - classifies and routes the request.
     * 
     * @param userId user identifier
     * @param startTime start of requested time range
     * @param endTime end of requested time range
     * @return timeline data for the requested period
     */
    public MovementTimelineDTO getTimeline(UUID userId, Instant startTime, Instant endTime) {
        log.debug("Timeline request for user {} from {} to {}", userId, startTime, endTime);
        
        RequestType requestType = analyzeRequest(startTime, endTime);
        log.debug("Request classified as: {}", requestType);
        
        return switch (requestType) {
            case PAST_ONLY -> pastRequestHandler.handle(userId, startTime, endTime);
            case MIXED -> mixedRequestHandler.handle(userId, startTime, endTime);
            case FUTURE_ONLY -> createEmptyTimeline(userId);
        };
    }

    /**
     * Analyze the request time range to determine processing strategy.
     */
    public RequestType analyzeRequest(Instant startTime, Instant endTime) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate startDate = startTime.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = endTime.atZone(ZoneOffset.UTC).toLocalDate();
        
        if (endDate.isBefore(today)) {
            // Both start and end are before today
            return RequestType.PAST_ONLY;
        } else if (startDate.isBefore(today) || startDate.equals(today)) {
            // Start is before today or today, end is today or after
            return RequestType.MIXED;
        } else {
            // Both start and end are after today
            return RequestType.FUTURE_ONLY;
        }
    }

    /**
     * Create empty timeline for future dates or error cases.
     */
    private MovementTimelineDTO createEmptyTimeline(UUID userId) {
        log.debug("Creating empty timeline for future dates");
        MovementTimelineDTO timeline = new MovementTimelineDTO(userId);
        timeline.setDataSource(TimelineDataSource.LIVE);
        timeline.setLastUpdated(Instant.now());
        return timeline;
    }

    /**
     * Request type classification for timeline processing.
     */
    public enum RequestType {
        /**
         * Both start and end dates are before today.
         * Use cached data or generate from scratch if missing.
         */
        PAST_ONLY,
        
        /**
         * Request spans past dates and today (or today only).
         * Combine cached past data with live today data.
         */
        MIXED,
        
        /**
         * Both start and end dates are after today.
         * Return empty timeline.
         */
        FUTURE_ONLY
    }
}