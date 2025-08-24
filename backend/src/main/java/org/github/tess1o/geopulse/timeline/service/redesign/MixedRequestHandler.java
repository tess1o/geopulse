package org.github.tess1o.geopulse.timeline.service.redesign;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.timeline.assembly.TimelineService;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineDataGapDTO;
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
        
        if (endDate.isBefore(today)) {
            // Request is entirely in the past - delegate to past handler
            log.debug("Request is entirely in the past - using past handler");
            return pastRequestHandler.handle(userId, startTime, endTime);
        }
        
        if (startDate.equals(today)) {
            // Request starts today and extends into future - generate live for the period
            log.debug("Request starts today and may extend to future - generating live timeline");
            return generateTodayTimeline(userId, startTime, endTime);
        }
        
        // Mixed request: spans past and today/future - split at today's boundary
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
            if (timeline != null && (timeline.getStaysCount() > 0 || timeline.getTripsCount() > 0)) {
                // Check if GPS data covers the full requested period
                Instant latestGpsTime = findLatestTimelineEventEnd(timeline);
                
                // For today's timeline, cap any gaps at current time (we don't know the future)
                Instant cappedEndTime = endTime.isAfter(Instant.now()) ? Instant.now() : endTime;
                
                if (latestGpsTime != null && latestGpsTime.isBefore(cappedEndTime)) {
                    // Add data gap for period after GPS data ends, capped to "now"
                    Instant gapStartTime = latestGpsTime.plusSeconds(1);
                    TimelineDataGapDTO dataGap = new TimelineDataGapDTO(gapStartTime, cappedEndTime);
                    timeline.getDataGaps().add(dataGap);
                    
                    log.debug("Added data gap for today from {} to {} (GPS ended at {}, capped to now)", 
                             gapStartTime, cappedEndTime, latestGpsTime);
                }
                
                // Timeline has actual data - return it as live
                timeline.setDataSource(TimelineDataSource.LIVE);
                timeline.setLastUpdated(Instant.now());
                return timeline;
            }
        } catch (Exception e) {
            log.error("Failed to generate today's timeline for user {} from {} to {}", 
                     userId, startTime, endTime, e);
        }
        
        // No timeline data generated (null or empty) - create timeline with data gap capped to "now"
        Instant cappedEndTime = endTime.isAfter(Instant.now()) ? Instant.now() : endTime;
        log.debug("No GPS data for today timeline - creating data gap from {} to {} (capped to now)", 
                 startTime, cappedEndTime);
        return createDataGapTimelineForToday(userId, startTime, cappedEndTime);
    }

    /**
     * Create timeline with data gap for today when no GPS data exists.
     * Similar to TimelineOvernightProcessor.createTimelineWithDataGap() but for live data.
     */
    private MovementTimelineDTO createDataGapTimelineForToday(UUID userId, Instant startTime, Instant endTime) {
        MovementTimelineDTO timeline = new MovementTimelineDTO(userId);
        timeline.setDataSource(TimelineDataSource.LIVE);
        timeline.setLastUpdated(Instant.now());
        
        // Add data gap for the requested period
        TimelineDataGapDTO dataGap = new TimelineDataGapDTO(startTime, endTime);
        timeline.getDataGaps().add(dataGap);
        
        log.debug("Created today timeline with data gap from {} to {}", startTime, endTime);
        
        return timeline;
    }

    /**
     * Find the latest end time among all timeline events (stays, trips, existing data gaps).
     * This helps determine if there's a gap between the last GPS activity and the requested end time.
     * 
     * @param timeline the timeline to analyze
     * @return the latest end time of any timeline event, or null if no events exist
     */
    private Instant findLatestTimelineEventEnd(MovementTimelineDTO timeline) {
        Instant latestTime = null;
        
        // Check stays
        for (var stay : timeline.getStays()) {
            Instant stayEndTime = stay.getTimestamp().plusSeconds(stay.getStayDuration() * 60L);
            if (latestTime == null || stayEndTime.isAfter(latestTime)) {
                latestTime = stayEndTime;
            }
        }
        
        // Check trips
        for (var trip : timeline.getTrips()) {
            Instant tripEndTime = trip.getTimestamp().plusSeconds(trip.getTripDuration() * 60L);
            if (latestTime == null || tripEndTime.isAfter(latestTime)) {
                latestTime = tripEndTime;
            }
        }
        
        // Check existing data gaps (in case timeline generation service already added some)
        for (var dataGap : timeline.getDataGaps()) {
            if (latestTime == null || dataGap.getEndTime().isAfter(latestTime)) {
                latestTime = dataGap.getEndTime();
            }
        }
        
        return latestTime;
    }
}