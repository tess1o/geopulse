package org.github.tess1o.geopulse.timeline.service.redesign;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.timeline.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.timeline.mapper.TimelinePersistenceMapper;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.github.tess1o.geopulse.timeline.model.TimelineDataGapDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineDataSource;
import org.github.tess1o.geopulse.timeline.model.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineTripDTO;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Complex logic coordinator for timeline assembly.
 * Handles previous context prepending, cross-day gap detection, and boundary expansion.
 */
@ApplicationScoped
@Slf4j
public class TimelineAssembler {

    @Inject
    TimelineEventRetriever timelineEventRetriever;
    
    @Inject
    TimelineConfigurationProvider configurationProvider;
    
    @Inject
    TimelinePersistenceMapper persistenceMapper;

    /**
     * Enhance timeline with boundary expansion and previous context prepending.
     * 
     * @param timeline base timeline to enhance
     * @param userId user identifier
     * @param requestStartTime original request start time
     * @param requestEndTime original request end time
     * @return enhanced timeline with prepending and boundary expansion
     */
    public MovementTimelineDTO enhanceTimeline(MovementTimelineDTO timeline, UUID userId, 
                                             Instant requestStartTime, Instant requestEndTime) {
        log.debug("Enhancing timeline for user {} with {} stays, {} trips, {} gaps", 
                 userId, timeline.getStaysCount(), timeline.getTripsCount(), timeline.getDataGapsCount());
        
        // Apply previous context prepending
        MovementTimelineDTO enhanced = prependPreviousContext(timeline, userId, requestStartTime);
        
        log.debug("Enhanced timeline now has {} stays, {} trips, {} gaps", 
                 enhanced.getStaysCount(), enhanced.getTripsCount(), enhanced.getDataGapsCount());
        
        return enhanced;
    }

    /**
     * Combine two timelines with cross-day gap detection.
     * 
     * @param pastTimeline timeline from past dates
     * @param todayTimeline timeline from today
     * @param userId user identifier for configuration
     * @return combined timeline with cross-day gaps detected
     */
    public MovementTimelineDTO combineTimelines(MovementTimelineDTO pastTimeline, 
                                              MovementTimelineDTO todayTimeline, 
                                              UUID userId) {
        log.debug("Combining timelines: past({} stays, {} trips, {} gaps) + today({} stays, {} trips, {} gaps)", 
                 pastTimeline.getStaysCount(), pastTimeline.getTripsCount(), pastTimeline.getDataGapsCount(),
                 todayTimeline.getStaysCount(), todayTimeline.getTripsCount(), todayTimeline.getDataGapsCount());
        
        MovementTimelineDTO combined = new MovementTimelineDTO(userId);
        
        // Add all events from both timelines
        combined.getStays().addAll(pastTimeline.getStays());
        combined.getStays().addAll(todayTimeline.getStays());
        combined.getTrips().addAll(pastTimeline.getTrips());
        combined.getTrips().addAll(todayTimeline.getTrips());
        combined.getDataGaps().addAll(pastTimeline.getDataGaps());
        combined.getDataGaps().addAll(todayTimeline.getDataGaps());
        
        // Detect cross-day gaps between timelines
        detectCrossDayGaps(pastTimeline, todayTimeline, combined, userId);
        
        // Sort all events chronologically
        combined.getStays().sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
        combined.getTrips().sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
        combined.getDataGaps().sort((a, b) -> a.getStartTime().compareTo(b.getStartTime()));
        
        // Merge adjacent/overlapping data gaps
        mergeAdjacentDataGaps(combined);
        
        combined.setLastUpdated(Instant.now());
        
        log.debug("Combined timeline result: {} stays, {} trips, {} gaps", 
                 combined.getStaysCount(), combined.getTripsCount(), combined.getDataGapsCount());
        
        return combined;
    }

    /**
     * Prepend previous context to show timeline continuity.
     * Finds the latest event before request start and adjusts its duration to show continuity.
     * Only applies when the timeline contains actual stays/trips (not just data gaps).
     */
    private MovementTimelineDTO prependPreviousContext(MovementTimelineDTO timeline, UUID userId, Instant requestStartTime) {
        try {
            log.debug("Looking for previous context before {} for user {}", requestStartTime, userId);
            
            // Don't prepend if timeline contains only data gaps (no actual GPS-based activities)
            if (timeline.getStaysCount() == 0 && timeline.getTripsCount() == 0) {
                log.debug("Timeline contains only data gaps, skipping previous context prepending");
                return timeline;
            }
            
            var latestEvent = timelineEventRetriever.findLatestEventBefore(userId, requestStartTime);
            if (latestEvent.isEmpty()) {
                log.debug("No previous context found");
                return timeline;
            }
            
            TimelineEventRetriever.TimelineEvent event = latestEvent.get();
            log.debug("Found latest event: {} at {}", event.getType(), event.getStartTime());
            
            // Find earliest activity in current timeline
            Instant earliestTimeInTimeline = findEarliestTimeInTimeline(timeline, requestStartTime);
            if (earliestTimeInTimeline == null) {
                log.debug("Current timeline is empty, not prepending");
                return timeline;
            }
            
            log.debug("Earliest time in timeline: {}", earliestTimeInTimeline);
            
            // Prepend the event with adjusted duration
            prependEventWithAdjustedDuration(timeline, event, earliestTimeInTimeline);
            
        } catch (Exception e) {
            log.warn("Failed to prepend previous context for user {}: {}", userId, e.getMessage());
        }
        
        return timeline;
    }

    /**
     * Detect gaps that span across the boundary between past and today timelines.
     */
    private void detectCrossDayGaps(MovementTimelineDTO pastTimeline, MovementTimelineDTO todayTimeline, 
                                   MovementTimelineDTO combined, UUID userId) {
        
        TimelineConfig config = configurationProvider.getConfigurationForUser(userId);
        Integer gapThresholdSeconds = config.getDataGapThresholdSeconds();
        Integer minGapDurationSeconds = config.getDataGapMinDurationSeconds();
        
        if (gapThresholdSeconds == null || minGapDurationSeconds == null) {
            log.debug("Cross-day gap detection disabled - thresholds not configured");
            return;
        }
        
        log.debug("Checking for cross-day gaps with threshold: {}s, min duration: {}s", 
                 gapThresholdSeconds, minGapDurationSeconds);
        
        // Find last activity timestamp in past timeline
        Instant lastPastActivity = getLastActivityTimestamp(pastTimeline);
        
        // Find first activity timestamp in today timeline
        Instant firstTodayActivity = getFirstActivityTimestamp(todayTimeline);
        
        if (lastPastActivity == null || firstTodayActivity == null) {
            log.debug("Cannot detect cross-day gap - missing activity timestamps (past: {}, today: {})", 
                     lastPastActivity, firstTodayActivity);
            return;
        }
        
        // Calculate gap duration
        long gapDurationSeconds = Duration.between(lastPastActivity, firstTodayActivity).getSeconds();
        
        log.debug("Cross-day gap analysis: {} to {} = {}s ({}h)", 
                lastPastActivity, firstTodayActivity, gapDurationSeconds, gapDurationSeconds / 3600.0);
        
        if (gapDurationSeconds > gapThresholdSeconds && gapDurationSeconds >= minGapDurationSeconds) {
            TimelineDataGapDTO crossDayGap = new TimelineDataGapDTO(lastPastActivity, firstTodayActivity, gapDurationSeconds);
            combined.getDataGaps().add(crossDayGap);
            
            log.info("✓ CROSS-DAY DATA GAP DETECTED: {} to {} (duration: {}s = {}h)", 
                    lastPastActivity, firstTodayActivity, gapDurationSeconds, gapDurationSeconds / 3600.0);
        } else {
            log.debug("Cross-day gap too short to record: {}s < {}s threshold", gapDurationSeconds, minGapDurationSeconds);
        }
    }

    /**
     * Find the earliest timestamp in timeline at or after the request start time.
     */
    private Instant findEarliestTimeInTimeline(MovementTimelineDTO timeline, Instant requestStartTime) {
        Instant earliest = null;
        
        // Check stays
        for (var stay : timeline.getStays()) {
            if (stay.getTimestamp().compareTo(requestStartTime) >= 0) {
                if (earliest == null || stay.getTimestamp().isBefore(earliest)) {
                    earliest = stay.getTimestamp();
                }
            }
        }
        
        // Check trips
        for (var trip : timeline.getTrips()) {
            if (trip.getTimestamp().compareTo(requestStartTime) >= 0) {
                if (earliest == null || trip.getTimestamp().isBefore(earliest)) {
                    earliest = trip.getTimestamp();
                }
            }
        }
        
        // Check data gaps
        for (var gap : timeline.getDataGaps()) {
            if (gap.getStartTime().compareTo(requestStartTime) >= 0) {
                if (earliest == null || gap.getStartTime().isBefore(earliest)) {
                    earliest = gap.getStartTime();
                }
            }
        }
        
        // If no activities found at or after request start time, use request start time itself
        if (earliest == null) {
            earliest = requestStartTime;
        }
        
        return earliest;
    }

    /**
     * Prepend an event with adjusted duration to show continuity.
     */
    private void prependEventWithAdjustedDuration(MovementTimelineDTO timeline, 
                                                 TimelineEventRetriever.TimelineEvent event, 
                                                 Instant endTime) {
        switch (event.getType()) {
            case STAY -> {
                // Create stay DTO with adjusted duration
                TimelineStayLocationDTO stayDTO = createAdjustedStayDTO(event, endTime);
                timeline.getStays().add(0, stayDTO);
                log.debug("Prepended stay: {} at {} (adjusted duration: {}min)", 
                         stayDTO.getLocationName(), stayDTO.getTimestamp(), stayDTO.getStayDuration());
            }
            case TRIP -> {
                // Create trip DTO with adjusted duration
                TimelineTripDTO tripDTO = createAdjustedTripDTO(event, endTime);
                timeline.getTrips().add(0, tripDTO);
                log.debug("Prepended trip: {} at {} (adjusted duration: {}min)", 
                         tripDTO.getMovementType(), tripDTO.getTimestamp(), tripDTO.getTripDuration());
            }
            case DATA_GAP -> {
                // Create data gap DTO with adjusted duration
                TimelineDataGapDTO gapDTO = new TimelineDataGapDTO(event.getStartTime(), endTime);
                timeline.getDataGaps().add(0, gapDTO);
                log.debug("Prepended data gap: {} to {} (adjusted duration: {}min)", 
                         gapDTO.getStartTime(), gapDTO.getEndTime(), gapDTO.getDurationMinutes());
            }
        }
    }

    /**
     * Create adjusted stay DTO for prepending.
     */
    private TimelineStayLocationDTO createAdjustedStayDTO(TimelineEventRetriever.TimelineEvent event, Instant endTime) {
        // This is a simplified version - in real implementation, we'd need to fetch the actual stay entity
        // to get location details, but for now we'll create a basic DTO
        long adjustedDurationMinutes = Duration.between(event.getStartTime(), endTime).toMinutes();
        
        TimelineStayLocationDTO stayDTO = new TimelineStayLocationDTO();
        stayDTO.setTimestamp(event.getStartTime());
        stayDTO.setStayDuration(adjustedDurationMinutes);
        stayDTO.setLocationName("Previous Context"); // Placeholder - would be fetched from DB
        
        return stayDTO;
    }

    /**
     * Create adjusted trip DTO for prepending.
     */
    private TimelineTripDTO createAdjustedTripDTO(TimelineEventRetriever.TimelineEvent event, Instant endTime) {
        // This is a simplified version - in real implementation, we'd need to fetch the actual trip entity
        long adjustedDurationMinutes = Duration.between(event.getStartTime(), endTime).toMinutes();
        
        TimelineTripDTO tripDTO = new TimelineTripDTO();
        tripDTO.setTimestamp(event.getStartTime());
        tripDTO.setTripDuration(adjustedDurationMinutes);
        tripDTO.setMovementType("Previous Context"); // Placeholder - would be fetched from DB
        
        return tripDTO;
    }

    /**
     * Get the last activity timestamp from a timeline.
     */
    private Instant getLastActivityTimestamp(MovementTimelineDTO timeline) {
        Instant lastTimestamp = null;
        
        // Check last stay
        if (!timeline.getStays().isEmpty()) {
            TimelineStayLocationDTO lastStay = timeline.getStays().get(timeline.getStays().size() - 1);
            Instant stayEndTime = lastStay.getTimestamp().plusSeconds(lastStay.getStayDuration() * 60);
            lastTimestamp = maxInstant(lastTimestamp, stayEndTime);
        }
        
        // Check last trip
        if (!timeline.getTrips().isEmpty()) {
            TimelineTripDTO lastTrip = timeline.getTrips().get(timeline.getTrips().size() - 1);
            Instant tripEndTime = lastTrip.getTimestamp().plusSeconds(lastTrip.getTripDuration() * 60);
            lastTimestamp = maxInstant(lastTimestamp, tripEndTime);
        }
        
        // Check last data gap
        if (!timeline.getDataGaps().isEmpty()) {
            TimelineDataGapDTO lastGap = timeline.getDataGaps().get(timeline.getDataGaps().size() - 1);
            lastTimestamp = maxInstant(lastTimestamp, lastGap.getEndTime());
        }
        
        return lastTimestamp;
    }

    /**
     * Get the first activity timestamp from a timeline.
     */
    private Instant getFirstActivityTimestamp(MovementTimelineDTO timeline) {
        Instant firstTimestamp = null;
        
        // Check first stay
        if (!timeline.getStays().isEmpty()) {
            TimelineStayLocationDTO firstStay = timeline.getStays().get(0);
            firstTimestamp = minInstant(firstTimestamp, firstStay.getTimestamp());
        }
        
        // Check first trip
        if (!timeline.getTrips().isEmpty()) {
            TimelineTripDTO firstTrip = timeline.getTrips().get(0);
            firstTimestamp = minInstant(firstTimestamp, firstTrip.getTimestamp());
        }
        
        // Check first data gap
        if (!timeline.getDataGaps().isEmpty()) {
            TimelineDataGapDTO firstGap = timeline.getDataGaps().get(0);
            firstTimestamp = minInstant(firstTimestamp, firstGap.getStartTime());
        }
        
        return firstTimestamp;
    }

    private Instant maxInstant(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private Instant minInstant(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    /**
     * Merge adjacent or overlapping data gaps into single continuous gaps.
     * This is essential for cases where past and today timelines both have data gaps
     * that should be presented as a single continuous period without GPS data.
     * 
     * @param timeline timeline to process for gap merging
     */
    private void mergeAdjacentDataGaps(MovementTimelineDTO timeline) {
        List<TimelineDataGapDTO> gaps = timeline.getDataGaps();
        if (gaps.size() <= 1) {
            log.debug("Timeline has {} gaps - no merging needed", gaps.size());
            return;
        }
        
        log.debug("Merging {} data gaps for adjacent/overlapping periods", gaps.size());
        
        // Gaps should already be sorted, but ensure chronological order
        gaps.sort((a, b) -> a.getStartTime().compareTo(b.getStartTime()));
        
        // Merge adjacent/overlapping gaps
        List<TimelineDataGapDTO> mergedGaps = new java.util.ArrayList<>();
        TimelineDataGapDTO currentGap = gaps.get(0);
        
        for (int i = 1; i < gaps.size(); i++) {
            TimelineDataGapDTO nextGap = gaps.get(i);
            
            // Check if gaps are adjacent (current ends exactly when next starts) 
            // or overlapping (current ends after next starts)
            if (isAdjacentOrOverlapping(currentGap, nextGap)) {
                // Merge gaps - start time remains, end time is the later of the two
                Instant mergedEndTime = currentGap.getEndTime().isAfter(nextGap.getEndTime()) 
                    ? currentGap.getEndTime() : nextGap.getEndTime();
                    
                log.debug("Merging adjacent gaps: {} → {} and {} → {} into {} → {}", 
                         currentGap.getStartTime(), currentGap.getEndTime(),
                         nextGap.getStartTime(), nextGap.getEndTime(),
                         currentGap.getStartTime(), mergedEndTime);
                         
                currentGap = new TimelineDataGapDTO(currentGap.getStartTime(), mergedEndTime);
            } else {
                // Gaps are not adjacent - add current gap and move to next
                mergedGaps.add(currentGap);
                currentGap = nextGap;
                log.debug("Gap not adjacent - keeping separate: {} → {} | {} → {}", 
                         mergedGaps.get(mergedGaps.size() - 1).getStartTime(), 
                         mergedGaps.get(mergedGaps.size() - 1).getEndTime(),
                         currentGap.getStartTime(), currentGap.getEndTime());
            }
        }
        
        // Add the last gap
        mergedGaps.add(currentGap);
        
        // Replace the original gaps with merged ones
        timeline.getDataGaps().clear();
        timeline.getDataGaps().addAll(mergedGaps);
        
        log.debug("Gap merging complete: {} original gaps merged into {} gaps", 
                 gaps.size(), mergedGaps.size());
    }

    /**
     * Check if two data gaps are adjacent (touching) or overlapping.
     * Adjacent means the first gap ends exactly when the second gap starts.
     * Overlapping means the first gap ends after the second gap starts.
     */
    private boolean isAdjacentOrOverlapping(TimelineDataGapDTO firstGap, TimelineDataGapDTO secondGap) {
        // Adjacent: first ends exactly when second starts (considering nanosecond precision)
        // We check for near-equality to handle potential nanosecond rounding issues
        Duration timeBetween = Duration.between(firstGap.getEndTime(), secondGap.getStartTime());
        
        // Adjacent if time between is 0 or very small (up to 1 nanosecond difference)
        boolean adjacent = timeBetween.abs().toNanos() <= 1;
        
        // Overlapping: first ends after second starts
        boolean overlapping = firstGap.getEndTime().isAfter(secondGap.getStartTime());
        
        boolean result = adjacent || overlapping;
        
        if (result) {
            log.debug("Gaps are {} - gap1: {} → {}, gap2: {} → {}, time between: {} ns", 
                     adjacent ? "adjacent" : "overlapping",
                     firstGap.getStartTime(), firstGap.getEndTime(),
                     secondGap.getStartTime(), secondGap.getEndTime(),
                     timeBetween.toNanos());
        }
        
        return result;
    }
}