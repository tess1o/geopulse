package org.github.tess1o.geopulse.timeline.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.timeline.assembly.TimelineService;
import org.github.tess1o.geopulse.timeline.model.*;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.timeline.repository.TimelineTripRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Processes timeline generation for whole days using the 4-step algorithm:
 * 1. Retrieve the last saved event from previous days
 * 2. Generate timeline from that event's start time to end of processing day
 * 3. Update existing event with new end time/duration from first generated event
 * 4. Save remaining generated events (skipping first one)
 * 
 * This ensures overnight stays are properly extended rather than cut at midnight.
 * This is the unified processor for all whole-day timeline generation.
 */
@ApplicationScoped
@Slf4j
public class WholeTimelineProcessor {

    @Inject
    TimelineService timelineGenerationService;

    @Inject
    TimelineQueryService timelineQueryService;

    @Inject
    TimelineStayRepository timelineStayRepository;

    @Inject
    TimelineTripRepository timelineTripRepository;

    @Inject
    TimelineCacheService timelineCacheService;

    /**
     * Process timeline for a user on a specific date, handling overnight stays.
     * Falls back to standard 00:00-23:59 processing if no previous events exist.
     *
     * @param userId user ID
     * @param processingDate the date to process (UTC)
     * @return MovementTimelineDTO with processed timeline data
     */
    @Transactional
    public MovementTimelineDTO processWholeTimeline(UUID userId, LocalDate processingDate) {
        log.debug("Processing whole-day timeline for user {} on date {}", userId, processingDate);

        Instant startOfProcessingDay = processingDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfProcessingDay = processingDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);

        // Step 1: Retrieve the last saved event from previous days
        LastSavedEvent lastSavedEvent = findLastSavedEvent(userId, startOfProcessingDay);

        if (lastSavedEvent == null) {
            // No previous events - fall back to standard processing
            log.debug("No previous events found for user {}, using standard 00:00-23:59 processing", userId);
            return generateStandardTimeline(userId, startOfProcessingDay, endOfProcessingDay);
        }

        log.debug("Found last saved event: {} at {}", lastSavedEvent.getType(), lastSavedEvent.getStartTime());

        // Step 2: Generate timeline from last event's start time to end of processing day
        Instant timelineStartTime = lastSavedEvent.getStartTime();
        MovementTimelineDTO generatedTimeline = timelineGenerationService.getMovementTimeline(
            userId, timelineStartTime, endOfProcessingDay);

        if (generatedTimeline == null || 
            (generatedTimeline.getStaysCount() == 0 && generatedTimeline.getTripsCount() == 0)) {
            log.debug("No timeline data generated from {}, falling back to standard processing", timelineStartTime);
            return generateStandardTimeline(userId, startOfProcessingDay, endOfProcessingDay);
        }

        // Step 3 & 4: Update existing event and save remaining events
        return processGeneratedTimeline(lastSavedEvent, generatedTimeline, startOfProcessingDay);
    }

    /**
     * Find the most recent saved event (stay or trip) before the given timestamp.
     */
    private LastSavedEvent findLastSavedEvent(UUID userId, Instant beforeTimestamp) {
        TimelineStayEntity latestStay = timelineStayRepository.findLatestBefore(userId, beforeTimestamp);
        TimelineTripEntity latestTrip = timelineTripRepository.findLatestBefore(userId, beforeTimestamp);

        log.debug("Found latest stay: {}, latest trip: {}", latestStay, latestTrip);

        // Determine which event is more recent
        if (latestStay != null && latestTrip != null) {
            if (latestStay.getTimestamp().isAfter(latestTrip.getTimestamp())) {
                return new LastSavedEvent(EventType.STAY, latestStay.getId(), latestStay.getTimestamp());
            } else {
                return new LastSavedEvent(EventType.TRIP, latestTrip.getId(), latestTrip.getTimestamp());
            }
        } else if (latestStay != null) {
            return new LastSavedEvent(EventType.STAY, latestStay.getId(), latestStay.getTimestamp());
        } else if (latestTrip != null) {
            return new LastSavedEvent(EventType.TRIP, latestTrip.getId(), latestTrip.getTimestamp());
        }

        return null; // No previous events found
    }

    /**
     * Process the generated timeline by updating existing event and saving new events.
     */
    private MovementTimelineDTO processGeneratedTimeline(LastSavedEvent lastSavedEvent, 
                                                         MovementTimelineDTO generatedTimeline,
                                                         Instant processingDayStart) {
        
        // Find the first generated event that corresponds to our last saved event
        if (lastSavedEvent.getType() == EventType.STAY) {
            return processStayTimeline(lastSavedEvent, generatedTimeline, processingDayStart);
        } else {
            return processTripTimeline(lastSavedEvent, generatedTimeline, processingDayStart);
        }
    }

    /**
     * Process timeline when last saved event was a stay.
     */
    private MovementTimelineDTO processStayTimeline(LastSavedEvent lastSavedEvent, 
                                                    MovementTimelineDTO generatedTimeline,
                                                    Instant processingDayStart) {
        
        if (generatedTimeline.getStaysCount() == 0) {
            log.debug("No stays in generated timeline, saving all generated events");
            return saveAllGeneratedEvents(generatedTimeline, processingDayStart);
        }

        // Step 3: Update existing stay with new duration from first generated stay
        TimelineStayLocationDTO firstGeneratedStay = generatedTimeline.getStays().get(0);
        Instant newEndTime = firstGeneratedStay.getTimestamp().plusSeconds(firstGeneratedStay.getStayDuration());
        long newDurationSeconds = Duration.between(lastSavedEvent.getStartTime(), newEndTime).getSeconds();

        log.debug("Updating existing stay {} with new duration: {} seconds (was {} seconds)", 
                 lastSavedEvent.getId(), newDurationSeconds, firstGeneratedStay.getStayDuration());

        long updatedRows = timelineStayRepository.updateEndTimeAndDuration(
            lastSavedEvent.getId(), newEndTime, newDurationSeconds);

        if (updatedRows != 1) {
            log.warn("Expected to update 1 stay, but updated {}", updatedRows);
        }

        // Step 4: Save remaining events (skip first generated stay)
        return saveRemainingEvents(generatedTimeline, processingDayStart, true);
    }

    /**
     * Process timeline when last saved event was a trip.
     */
    private MovementTimelineDTO processTripTimeline(LastSavedEvent lastSavedEvent, 
                                                    MovementTimelineDTO generatedTimeline,
                                                    Instant processingDayStart) {
        
        if (generatedTimeline.getTripsCount() == 0) {
            log.debug("No trips in generated timeline, saving all generated events");
            return saveAllGeneratedEvents(generatedTimeline, processingDayStart);
        }

        // Step 3: Update existing trip with new duration from first generated trip
        TimelineTripDTO firstGeneratedTrip = generatedTimeline.getTrips().get(0);
        Instant newEndTime = firstGeneratedTrip.getTimestamp().plus(Duration.ofMinutes(firstGeneratedTrip.getTripDuration()));
        long newDurationMinutes = Duration.between(lastSavedEvent.getStartTime(), newEndTime).toMinutes();

        log.debug("Updating existing trip {} with new duration: {} minutes (was {} minutes)", 
                 lastSavedEvent.getId(), newDurationMinutes, firstGeneratedTrip.getTripDuration());

        long updatedRows = timelineTripRepository.updateEndTimeAndDuration(
            lastSavedEvent.getId(), newEndTime, newDurationMinutes);

        if (updatedRows != 1) {
            log.warn("Expected to update 1 trip, but updated {}", updatedRows);
        }

        // Step 4: Save remaining events (skip first generated trip)
        return saveRemainingEvents(generatedTimeline, processingDayStart, false);
    }

    /**
     * Save remaining events after updating the existing one.
     */
    private MovementTimelineDTO saveRemainingEvents(MovementTimelineDTO generatedTimeline, 
                                                    Instant processingDayStart, boolean skipFirstStay) {
        
        MovementTimelineDTO resultTimeline = new MovementTimelineDTO(generatedTimeline.getUserId());
        resultTimeline.setDataSource(TimelineDataSource.CACHED);
        resultTimeline.setLastUpdated(Instant.now());

        // Save remaining stays (skip first if it was used to update existing event)
        int stayStartIndex = skipFirstStay ? 1 : 0;
        for (int i = stayStartIndex; i < generatedTimeline.getStaysCount(); i++) {
            TimelineStayLocationDTO stay = generatedTimeline.getStays().get(i);
            
            // Only save events from processing day onwards
            if (stay.getTimestamp().compareTo(processingDayStart) >= 0) {
                resultTimeline.getStays().add(stay);
            }
        }

        // Save remaining trips (skip first if it was used to update existing event)  
        int tripStartIndex = skipFirstStay ? 0 : 1;
        for (int i = tripStartIndex; i < generatedTimeline.getTripsCount(); i++) {
            TimelineTripDTO trip = generatedTimeline.getTrips().get(i);
            
            // Only save events from processing day onwards
            if (trip.getTimestamp().compareTo(processingDayStart) >= 0) {
                resultTimeline.getTrips().add(trip);
            }
        }

        // Cache the new timeline data
        Instant endOfProcessingDay = processingDayStart.plus(Duration.ofDays(1)).minusNanos(1);
        timelineCacheService.save(generatedTimeline.getUserId(), processingDayStart, endOfProcessingDay, resultTimeline);

        log.debug("Saved {} remaining stays and {} remaining trips for processing day", 
                 resultTimeline.getStaysCount(), resultTimeline.getTripsCount());

        return resultTimeline;
    }

    /**
     * Save all generated events when no existing event needs updating.
     */
    private MovementTimelineDTO saveAllGeneratedEvents(MovementTimelineDTO generatedTimeline, 
                                                       Instant processingDayStart) {
        return saveRemainingEvents(generatedTimeline, processingDayStart, false);
    }

    /**
     * Generate standard 00:00-23:59 timeline when no previous events exist.
     * Uses TimelineService directly to avoid circular dependency.
     */
    private MovementTimelineDTO generateStandardTimeline(UUID userId, Instant startOfDay, Instant endOfDay) {
        log.debug("Generating standard timeline for user {} from {} to {}", userId, startOfDay, endOfDay);
        
        // Use TimelineService directly to avoid circular dependency with TimelineQueryService
        MovementTimelineDTO timeline = timelineGenerationService.getMovementTimeline(userId, startOfDay, endOfDay);
        if (timeline != null) {
            timeline.setDataSource(TimelineDataSource.CACHED);
            timeline.setLastUpdated(Instant.now());
            
            // Cache the generated timeline (same as overnight processing does)
            timelineCacheService.save(userId, startOfDay, endOfDay, timeline);
            log.debug("Cached standard timeline: {} stays, {} trips", 
                     timeline.getStaysCount(), timeline.getTripsCount());
        }

        return timeline;
    }

    /**
     * Represents the last saved event (stay or trip) before timeline processing.
     */
    private static class LastSavedEvent {
        private final EventType type;
        private final Long id;
        private final Instant startTime;

        public LastSavedEvent(EventType type, Long id, Instant startTime) {
            this.type = type;
            this.id = id;
            this.startTime = startTime;
        }

        public EventType getType() { return type; }
        public Long getId() { return id; }
        public Instant getStartTime() { return startTime; }
    }

    private enum EventType {
        STAY, TRIP
    }
}