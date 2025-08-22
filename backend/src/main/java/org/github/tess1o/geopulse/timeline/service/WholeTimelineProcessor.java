package org.github.tess1o.geopulse.timeline.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.timeline.assembly.TimelineService;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.timeline.model.*;
import org.github.tess1o.geopulse.timeline.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.timeline.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.user.repository.UserRepository;

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
    TimelineStayRepository timelineStayRepository;

    @Inject
    TimelineTripRepository timelineTripRepository;

    @Inject
    TimelineCacheService timelineCacheService;

    @Inject
    TimelineDataGapRepository timelineDataGapRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

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
     * Process timeline for a user across a multi-day date range, handling data gaps properly.
     * This method generates timeline for the entire range as a single unit, ensuring proper
     * data gap detection at the end of available GPS data.
     *
     * @param userId user ID
     * @param startTime start of the time range
     * @param endTime end of the time range
     * @return MovementTimelineDTO with processed timeline data including proper data gaps
     */
    @Transactional
    public MovementTimelineDTO processMultiDayTimeline(UUID userId, Instant startTime, Instant endTime) {
        log.debug("Processing multi-day timeline for user {} from {} to {}", userId, startTime, endTime);

        // Find the last saved event before the start time to handle overnight stays
        LastSavedEvent lastSavedEvent = findLastSavedEvent(userId, startTime);
        
        Instant actualStartTime = startTime;
        if (lastSavedEvent != null) {
            log.debug("Found last saved event: {} at {}", lastSavedEvent.getType(), lastSavedEvent.getStartTime());
            actualStartTime = lastSavedEvent.getStartTime();
        }

        // Generate timeline for the entire range as a single unit
        MovementTimelineDTO timeline = timelineGenerationService.getMovementTimeline(userId, actualStartTime, endTime);
        
        if (timeline == null) {
            // No timeline generated - check if there are existing data gaps for this period
            MovementTimelineDTO existingGapTimeline = getExistingDataGaps(userId, startTime, endTime);
            if (existingGapTimeline != null && existingGapTimeline.getDataGapsCount() > 0) {
                return existingGapTimeline;
            }
            // No existing gaps found - create new one
            return createTimelineWithDataGap(userId, startTime, endTime, TimelineDataSource.CACHED);
        }

        // If we started earlier due to a previous event, update that event and remove it from the new timeline
        if (lastSavedEvent != null && timeline.getStaysCount() > 0) {
            processGeneratedTimelineForRange(lastSavedEvent, timeline, startTime);
        }

        // Check if we need to add data gaps for periods where GPS data is missing
        // This handles cases where GPS data ends before the requested end time
        // TEMPORARILY DISABLED: Only add gaps if timeline generation didn't already detect them
        // if (timeline.getDataGapsCount() == 0) {
            ensureDataGapsForMissingPeriods(timeline, userId, startTime, endTime);
        // }

        // CRITICAL: Save the timeline to database and cache (this was missing!)
        timelineCacheService.save(userId, startTime, endTime, timeline);
        
        // Set proper metadata
        timeline.setDataSource(TimelineDataSource.CACHED);
        timeline.setLastUpdated(Instant.now());
        
        log.debug("Generated multi-day timeline: {} stays, {} trips, {} data gaps", 
                 timeline.getStaysCount(), timeline.getTripsCount(), timeline.getDataGapsCount());

        return timeline;
    }

    /**
     * Process generated timeline for multi-day range, updating existing events properly.
     */
    private void processGeneratedTimelineForRange(LastSavedEvent lastSavedEvent, MovementTimelineDTO timeline, Instant rangeStartTime) {
        if (timeline.getStaysCount() == 0 && timeline.getTripsCount() == 0) {
            return;
        }

        // Similar logic to processGeneratedTimeline but for multi-day ranges
        if (lastSavedEvent.getType() == EventType.STAY && timeline.getStaysCount() > 0) {
            TimelineStayLocationDTO firstStay = timeline.getStays().get(0);
            
            // Update the existing stay in database
            timelineStayRepository.findByIdOptional(lastSavedEvent.getId())
                .ifPresent(existingStay -> {
                    Duration extendedDuration = Duration.between(existingStay.getTimestamp(), firstStay.getTimestamp().plus(Duration.ofSeconds(firstStay.getStayDuration())));
                    existingStay.setStayDuration(extendedDuration.toSeconds());
                    log.debug("Updated existing stay {} with extended duration: {} seconds", existingStay.getId(), extendedDuration.toSeconds());
                });

            // Remove first stay from timeline since we updated the existing one
            timeline.getStays().remove(0);
        }
        // Similar logic can be added for trips if needed
    }

    /**
     * Create a timeline with a data gap covering the entire requested period when no GPS data exists.
     */
    private MovementTimelineDTO createTimelineWithDataGap(UUID userId, Instant startTime, Instant endTime, TimelineDataSource dataSource) {
        log.debug("Creating timeline with data gap for user {} from {} to {}", userId, startTime, endTime);
        
        MovementTimelineDTO timeline = new MovementTimelineDTO(userId);
        timeline.setDataSource(dataSource);
        timeline.setLastUpdated(Instant.now());
        
        // Add data gap for the entire period
        TimelineDataGapDTO dataGap = new TimelineDataGapDTO(startTime, endTime);
        timeline.getDataGaps().add(dataGap);
        
        // Persist the data gap to database
        userRepository.findByIdOptional(userId).ifPresent(user -> {
            TimelineDataGapEntity dataGapEntity = TimelineDataGapEntity.builder()
                .user(user)
                .startTime(startTime)
                .endTime(endTime)
                .build();
            timelineDataGapRepository.persist(dataGapEntity);
            log.debug("Persisted data gap entity for entire range: {} to {}", startTime, endTime);
        });
        
        return timeline;
    }

    /**
     * Get existing data gaps from database that overlap with the requested time range.
     * This handles cases where a timeline request covers a period that already has persisted data gaps.
     */
    private MovementTimelineDTO getExistingDataGaps(UUID userId, Instant startTime, Instant endTime) {
        log.debug("Looking for existing data gaps for user {} from {} to {}", userId, startTime, endTime);
        
        // Find data gaps that overlap with the requested time range
        var existingGaps = timelineDataGapRepository.findByUserIdAndTimeRange(userId, startTime, endTime);
        
        if (existingGaps.isEmpty()) {
            return null;
        }
        
        log.debug("Found {} existing data gaps that overlap with requested period", existingGaps.size());
        
        // Create timeline with existing gaps
        MovementTimelineDTO timeline = new MovementTimelineDTO(userId);
        timeline.setDataSource(TimelineDataSource.CACHED);
        timeline.setLastUpdated(Instant.now());
        
        // Convert entities to DTOs, but only include gaps that actually overlap with the requested range
        for (var gapEntity : existingGaps) {
            // Calculate the intersection of the existing gap with the requested period
            Instant gapStart = gapEntity.getStartTime().isAfter(startTime) ? gapEntity.getStartTime() : startTime;
            Instant gapEnd = gapEntity.getEndTime().isBefore(endTime) ? gapEntity.getEndTime() : endTime;
            
            // Only include if there's actual overlap
            if (gapStart.isBefore(gapEnd)) {
                TimelineDataGapDTO gapDto = new TimelineDataGapDTO(gapStart, gapEnd);
                timeline.getDataGaps().add(gapDto);
                log.debug("Added existing data gap: {} to {}", gapStart, gapEnd);
            }
        }
        
        return timeline.getDataGapsCount() > 0 ? timeline : null;
    }

    /**
     * Ensure that data gaps are created for periods where GPS data is missing.
     * This checks if the timeline covers the full requested period and adds data gaps as needed.
     */
    private void ensureDataGapsForMissingPeriods(MovementTimelineDTO timeline, UUID userId, Instant requestStartTime, Instant requestEndTime) {
        // Check if we have GPS data for the entire requested period
        // We'll query for the latest GPS point to see where our data ends
        
        // First, get the actual GPS data range to compare with the requested range
        Instant latestGpsTime = gpsPointRepository.findLatestTimestamp(userId, requestStartTime, requestEndTime);
        
        if (latestGpsTime == null) {
            // No GPS data at all - entire period should be a data gap
            createAndPersistDataGap(timeline, userId, requestStartTime, requestEndTime);
            return;
        }

        // If GPS data ends before the requested end time, create a data gap for the missing period
        if (latestGpsTime.isBefore(requestEndTime)) {
            Instant gapStartTime = latestGpsTime.plusSeconds(1); // Start gap right after last GPS point
            createAndPersistDataGap(timeline, userId, gapStartTime, requestEndTime);
            
            log.debug("Created data gap from {} to {} (GPS data ended at {})", 
                     gapStartTime, requestEndTime, latestGpsTime);
        }
    }

    /**
     * Create a data gap DTO and persist it to the database.
     */
    private void createAndPersistDataGap(MovementTimelineDTO timeline, UUID userId, Instant gapStartTime, Instant gapEndTime) {
        // Add data gap DTO to timeline
        TimelineDataGapDTO dataGap = new TimelineDataGapDTO(gapStartTime, gapEndTime);
        timeline.getDataGaps().add(dataGap);
        
        // Persist the data gap entity to database
        userRepository.findByIdOptional(userId).ifPresent(user -> {
            TimelineDataGapEntity dataGapEntity = TimelineDataGapEntity.builder()
                .user(user)
                .startTime(gapStartTime)
                .endTime(gapEndTime)
                .build();
            timelineDataGapRepository.persist(dataGapEntity);
            log.debug("Persisted data gap entity: {} to {}", gapStartTime, gapEndTime);
        });
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