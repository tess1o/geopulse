package org.github.tess1o.geopulse.timeline.service.redesign;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.timeline.assembly.TimelineService;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineDataSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Handles timeline requests that span past dates and today.
 * Strategy: Get past data from cache + generate today live + combine with cross-day gap detection.
 */
@ApplicationScoped
@Slf4j
public class MixedRequestHandler {

    @Inject
    PastRequestHandler pastRequestHandler;
    
    @Inject
    TimelineService timelineGenerationService;
    
    @Inject
    TimelineAssembler timelineAssembler;

    /**
     * Handle mixed timeline requests (past + today).
     * 
     * @param userId user identifier
     * @param startTime start of requested time range
     * @param endTime end of requested time range
     * @return timeline data combining past and today
     */
    public MovementTimelineDTO handle(UUID userId, Instant startTime, Instant endTime) {
        log.debug("Handling mixed request for user {} from {} to {}", userId, startTime, endTime);
        
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate startDate = startTime.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = endTime.atZone(ZoneOffset.UTC).toLocalDate();
        
        if (startDate.equals(today) && endDate.equals(today)) {
            // Request is only for today - generate live
            log.debug("Request is today only - generating live");
            return generateTodayTimeline(userId, startTime, endTime);
        }
        
        if (startDate.equals(today)) {
            // Request starts today - just generate live for today portion
            log.debug("Request starts today - generating live timeline");
            return generateTodayTimeline(userId, startTime, endTime);
        }
        
        // Split request at today's boundary
        Instant todayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant effectiveEndTime = endDate.isAfter(today) ? 
            today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1) : endTime;
        
        // Get past portion (cached)
        MovementTimelineDTO pastTimeline = pastRequestHandler.handle(userId, startTime, todayStart.minusNanos(1));
        
        // Get today portion (live)
        MovementTimelineDTO todayTimeline = generateTodayTimeline(userId, todayStart, effectiveEndTime);
        
        // Combine with cross-day gap detection
        MovementTimelineDTO combinedTimeline = timelineAssembler.combineTimelines(pastTimeline, todayTimeline, userId);
        combinedTimeline.setDataSource(TimelineDataSource.MIXED);
        
        log.debug("Combined timeline: {} stays, {} trips, {} data gaps", 
                 combinedTimeline.getStaysCount(), combinedTimeline.getTripsCount(), combinedTimeline.getDataGapsCount());
        
        return combinedTimeline;
    }

    /**
     * Generate live timeline for today.
     */
    private MovementTimelineDTO generateTodayTimeline(UUID userId, Instant startTime, Instant endTime) {
        try {
            MovementTimelineDTO timeline = timelineGenerationService.getMovementTimeline(userId, startTime, endTime);
            if (timeline != null) {
                timeline.setDataSource(TimelineDataSource.LIVE);
                timeline.setLastUpdated(Instant.now());
                return timeline;
            }
        } catch (Exception e) {
            log.error("Failed to generate today's timeline for user {} from {} to {}", 
                     userId, startTime, endTime, e);
        }
        
        // Return empty timeline on failure
        MovementTimelineDTO emptyTimeline = new MovementTimelineDTO(userId);
        emptyTimeline.setDataSource(TimelineDataSource.LIVE);
        emptyTimeline.setLastUpdated(Instant.now());
        return emptyTimeline;
    }
}