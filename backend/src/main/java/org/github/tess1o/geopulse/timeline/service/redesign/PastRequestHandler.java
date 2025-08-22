package org.github.tess1o.geopulse.timeline.service.redesign;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;

import java.time.Instant;
import java.util.UUID;

/**
 * Handles timeline requests for past dates only.
 * Strategy: Check cache → Use existing data or regenerate from scratch.
 */
@ApplicationScoped
@Slf4j
public class PastRequestHandler {

    @Inject
    TimelineEventRetriever timelineEventRetriever;
    
    @Inject
    TimelineOvernightProcessor overnightProcessor;
    
    @Inject
    TimelineAssembler timelineAssembler;

    /**
     * Handle past-only timeline requests.
     * 
     * @param userId user identifier
     * @param startTime start of requested time range
     * @param endTime end of requested time range
     * @return timeline data for the past period
     */
    public MovementTimelineDTO handle(UUID userId, Instant startTime, Instant endTime) {
        log.debug("Handling past request for user {} from {} to {}", userId, startTime, endTime);
        
        // Check if we have complete data in cache
        if (timelineEventRetriever.hasCompleteData(userId, startTime, endTime)) {
            log.debug("Found complete cached data");
            MovementTimelineDTO timeline = timelineEventRetriever.getExistingEvents(userId, startTime, endTime);
            
            // Apply boundary expansion and previous context prepending
            return timelineAssembler.enhanceTimeline(timeline, userId, startTime, endTime);
        }
        
        log.debug("No complete cached data - regenerating from scratch");
        
        // Delete existing partial data and regenerate from scratch
        timelineEventRetriever.deleteTimelineData(userId, startTime, endTime);
        
        // Generate new timeline using overnight processor
        MovementTimelineDTO newTimeline = overnightProcessor.processTimeRange(userId, startTime, endTime);
        
        // Apply enhancements (boundary expansion, prepending)
        return timelineAssembler.enhanceTimeline(newTimeline, userId, startTime, endTime);
    }
}