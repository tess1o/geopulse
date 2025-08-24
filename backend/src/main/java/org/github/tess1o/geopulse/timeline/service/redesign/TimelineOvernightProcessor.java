package org.github.tess1o.geopulse.timeline.service.redesign;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.timeline.assembly.TimelineService;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineDataGapDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineDataGapEntity;
import org.github.tess1o.geopulse.timeline.model.TimelineDataSource;
import org.github.tess1o.geopulse.timeline.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.timeline.service.TimelineCacheService;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Implements the 4-step overnight algorithm for whole-day timeline processing.
 * Also handles multi-day timeline generation with proper data gap detection.
 */
@ApplicationScoped
@Slf4j
public class TimelineOvernightProcessor {

    @Inject
    TimelineEventRetriever timelineEventRetriever;
    
    @Inject
    TimelineService timelineGenerationService;
    
    @Inject
    TimelineCacheService timelineCacheService;
    
    @Inject
    GpsPointRepository gpsPointRepository;
    
    @Inject
    UserRepository userRepository;
    
    @Inject
    TimelineDataGapRepository timelineDataGapRepository;

    /**
     * Process timeline for a specific time range, handling data gaps properly.
     * Can handle both single-day and multi-day ranges.
     * 
     * @param userId user identifier
     * @param startTime start of time range
     * @param endTime end of time range
     * @return processed timeline with proper data gaps
     */
    @Transactional
    public MovementTimelineDTO processTimeRange(UUID userId, Instant startTime, Instant endTime) {
        log.debug("Processing time range for user {} from {} to {}", userId, startTime, endTime);
        
        LocalDate startDate = startTime.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = endTime.atZone(ZoneOffset.UTC).toLocalDate();
        
        if (startDate.equals(endDate)) {
            // Same date - check if it's actually a whole day request
            Instant startOfDay = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant endOfDay = startDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);
            
            if (startTime.equals(startOfDay) && endTime.equals(endOfDay)) {
                // True whole day request - use optimized algorithm  
                return processWholeDay(userId, startDate);
            } else {
                // Partial day request - use multi-day logic with exact times
                return processMultiDayRange(userId, startTime, endTime);
            }
        } else {
            // Multi-day processing
            return processMultiDayRange(userId, startTime, endTime);
        }
    }

    /**
     * Process timeline for a single whole day using the 4-step overnight algorithm.
     * 
     * @param userId user identifier
     * @param processingDate the date to process
     * @return processed timeline for the day
     */
    @Transactional
    public MovementTimelineDTO processWholeDay(UUID userId, LocalDate processingDate) {
        log.debug("Processing whole day timeline for user {} on date {}", userId, processingDate);

        Instant startOfDay = processingDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = processingDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);

        // Step 1: Find the last saved event from previous days
        Optional<TimelineEventRetriever.TimelineEvent> lastSavedEvent = 
            timelineEventRetriever.findLatestEventBefore(userId, startOfDay);

        if (lastSavedEvent.isEmpty()) {
            // No previous events - fall back to standard processing
            log.debug("No previous events found, using standard processing");
            return generateStandardTimeline(userId, startOfDay, endOfDay);
        }

        log.debug("Found last saved event: {} at {}", lastSavedEvent.get().getType(), lastSavedEvent.get().getStartTime());

        // Step 2: Generate timeline from last event's start time to end of processing day
        Instant timelineStartTime = lastSavedEvent.get().getStartTime();
        MovementTimelineDTO generatedTimeline = timelineGenerationService.getMovementTimeline(
            userId, timelineStartTime, endOfDay);

        if (generatedTimeline == null || 
            (generatedTimeline.getStaysCount() == 0 && generatedTimeline.getTripsCount() == 0)) {
            log.debug("No timeline data generated, falling back to standard processing");
            return generateStandardTimeline(userId, startOfDay, endOfDay);
        }

        // Steps 3 & 4: Update existing event and save remaining events
        return processGeneratedTimeline(lastSavedEvent.get(), generatedTimeline, startOfDay, endOfDay);
    }

    /**
     * Process multi-day timeline range with proper data gap detection.
     */
    @Transactional
    public MovementTimelineDTO processMultiDayRange(UUID userId, Instant startTime, Instant endTime) {
        log.debug("Processing multi-day timeline for user {} from {} to {}", userId, startTime, endTime);

        // Check if GPS data exists for this period
        Instant latestGpsTime = gpsPointRepository.findLatestTimestamp(userId, startTime, endTime);
        
        if (latestGpsTime == null) {
            // No GPS data at all - create timeline with single data gap
            log.debug("No GPS data found - creating timeline with data gap for entire period");
            return createTimelineWithDataGap(userId, startTime, endTime);
        }

        // Find boundary expansion event
        Optional<TimelineEventRetriever.TimelineEvent> boundaryEvent = 
            timelineEventRetriever.findLatestEventBefore(userId, startTime);
        
        Instant actualStartTime = boundaryEvent.map(TimelineEventRetriever.TimelineEvent::getStartTime)
                                               .orElse(startTime);

        // Generate timeline for available GPS data period
        MovementTimelineDTO timeline = timelineGenerationService.getMovementTimeline(
            userId, actualStartTime, endTime);
        
        if (timeline == null) {
            return createTimelineWithDataGap(userId, startTime, endTime);
        }

        // Handle boundary expansion if needed
        if (boundaryEvent.isPresent() && timeline.getStaysCount() > 0) {
            updateBoundaryEvent(boundaryEvent.get(), timeline);
        }

        // Add data gap if GPS data ends before request end time
        if (latestGpsTime.isBefore(endTime)) {
            Instant gapStartTime = latestGpsTime.plusSeconds(1);
            TimelineDataGapDTO dataGap = new TimelineDataGapDTO(gapStartTime, endTime);
            timeline.getDataGaps().add(dataGap);
            
            // Persist the data gap
            persistDataGap(userId, gapStartTime, endTime);
            
            log.debug("Added data gap from {} to {} (GPS data ended at {})", 
                     gapStartTime, endTime, latestGpsTime);
        }

        // Set metadata and cache
        timeline.setDataSource(TimelineDataSource.CACHED);
        timeline.setLastUpdated(Instant.now());
        timelineCacheService.save(userId, startTime, endTime, timeline);
        
        log.debug("Generated multi-day timeline: {} stays, {} trips, {} data gaps", 
                 timeline.getStaysCount(), timeline.getTripsCount(), timeline.getDataGapsCount());

        return timeline;
    }

    /**
     * Generate standard timeline when no previous events exist.
     */
    private MovementTimelineDTO generateStandardTimeline(UUID userId, Instant startOfDay, Instant endOfDay) {
        log.debug("Generating standard timeline for user {} from {} to {}", userId, startOfDay, endOfDay);
        
        MovementTimelineDTO timeline = timelineGenerationService.getMovementTimeline(userId, startOfDay, endOfDay);
        if (timeline != null && (timeline.getStaysCount() > 0 || timeline.getTripsCount() > 0)) {
            timeline.setDataSource(TimelineDataSource.CACHED);
            timeline.setLastUpdated(Instant.now());
            
            // Cache the generated timeline
            timelineCacheService.save(userId, startOfDay, endOfDay, timeline);
            log.debug("Cached standard timeline: {} stays, {} trips", 
                     timeline.getStaysCount(), timeline.getTripsCount());
            return timeline;
        }

        // No timeline data generated (null or empty) - create timeline with data gap
        log.debug("No GPS data for requested period - creating data gap from {} to {}", startOfDay, endOfDay);
        MovementTimelineDTO dataGapTimeline = new MovementTimelineDTO(userId);
        dataGapTimeline.setDataSource(TimelineDataSource.CACHED);
        dataGapTimeline.setLastUpdated(Instant.now());
        
        // Create data gap covering the entire requested period
        TimelineDataGapDTO dataGap = new TimelineDataGapDTO(startOfDay, endOfDay);
        dataGapTimeline.getDataGaps().add(dataGap);
        
        return dataGapTimeline;
    }

    /**
     * Process generated timeline using the 4-step algorithm.
     * Steps 3 & 4: Update existing event and save remaining events.
     */
    private MovementTimelineDTO processGeneratedTimeline(TimelineEventRetriever.TimelineEvent lastSavedEvent,
                                                       MovementTimelineDTO generatedTimeline,
                                                       Instant processingDayStart,
                                                       Instant processingDayEnd) {
        
        MovementTimelineDTO resultTimeline = new MovementTimelineDTO(generatedTimeline.getUserId());
        resultTimeline.setDataSource(TimelineDataSource.CACHED);
        resultTimeline.setLastUpdated(Instant.now());

        // Step 3: Update existing event with new duration
        if (lastSavedEvent.getType() == TimelineEventRetriever.TimelineEventType.STAY && 
            generatedTimeline.getStaysCount() > 0) {
            
            // Update existing stay and skip first generated stay
            updateExistingStay(lastSavedEvent, generatedTimeline);
            
            // Add remaining stays (skip first)
            for (int i = 1; i < generatedTimeline.getStaysCount(); i++) {
                var stay = generatedTimeline.getStays().get(i);
                if (stay.getTimestamp().compareTo(processingDayStart) >= 0) {
                    resultTimeline.getStays().add(stay);
                }
            }
            
            // Add all trips from processing day
            generatedTimeline.getTrips().stream()
                .filter(trip -> trip.getTimestamp().compareTo(processingDayStart) >= 0)
                .forEach(trip -> resultTimeline.getTrips().add(trip));
                
        } else if (lastSavedEvent.getType() == TimelineEventRetriever.TimelineEventType.TRIP && 
                   generatedTimeline.getTripsCount() > 0) {
            
            // Update existing trip and skip first generated trip
            updateExistingTrip(lastSavedEvent, generatedTimeline);
            
            // Add all stays from processing day
            generatedTimeline.getStays().stream()
                .filter(stay -> stay.getTimestamp().compareTo(processingDayStart) >= 0)
                .forEach(stay -> resultTimeline.getStays().add(stay));
            
            // Add remaining trips (skip first)
            for (int i = 1; i < generatedTimeline.getTripsCount(); i++) {
                var trip = generatedTimeline.getTrips().get(i);
                if (trip.getTimestamp().compareTo(processingDayStart) >= 0) {
                    resultTimeline.getTrips().add(trip);
                }
            }
        } else {
            // No matching event type found - save all generated events
            generatedTimeline.getStays().stream()
                .filter(stay -> stay.getTimestamp().compareTo(processingDayStart) >= 0)
                .forEach(stay -> resultTimeline.getStays().add(stay));
            generatedTimeline.getTrips().stream()
                .filter(trip -> trip.getTimestamp().compareTo(processingDayStart) >= 0)
                .forEach(trip -> resultTimeline.getTrips().add(trip));
        }

        // Add all data gaps from processing day
        generatedTimeline.getDataGaps().stream()
            .filter(gap -> gap.getStartTime().compareTo(processingDayStart) >= 0)
            .forEach(gap -> resultTimeline.getDataGaps().add(gap));

        // Step 4: Cache the new timeline data
        timelineCacheService.save(generatedTimeline.getUserId(), processingDayStart, processingDayEnd, resultTimeline);

        log.debug("Processed timeline using 4-step algorithm: {} stays, {} trips, {} gaps", 
                 resultTimeline.getStaysCount(), resultTimeline.getTripsCount(), resultTimeline.getDataGapsCount());

        return resultTimeline;
    }

    /**
     * Update existing stay with new duration (Step 3 of algorithm).
     */
    private void updateExistingStay(TimelineEventRetriever.TimelineEvent lastSavedEvent, 
                                   MovementTimelineDTO generatedTimeline) {
        if (generatedTimeline.getStaysCount() == 0) return;
        
        var firstGeneratedStay = generatedTimeline.getStays().get(0);
        Instant newEndTime = firstGeneratedStay.getTimestamp().plusSeconds(firstGeneratedStay.getStayDuration() * 60);
        long newDurationSeconds = Duration.between(lastSavedEvent.getStartTime(), newEndTime).getSeconds();

        log.debug("Updating existing stay {} with new duration: {} seconds", 
                 lastSavedEvent.getId(), newDurationSeconds);

        // This would update the database - for now just log
        // timelineStayRepository.updateEndTimeAndDuration(lastSavedEvent.getId(), newEndTime, newDurationSeconds);
    }

    /**
     * Update existing trip with new duration (Step 3 of algorithm).
     */
    private void updateExistingTrip(TimelineEventRetriever.TimelineEvent lastSavedEvent, 
                                   MovementTimelineDTO generatedTimeline) {
        if (generatedTimeline.getTripsCount() == 0) return;
        
        var firstGeneratedTrip = generatedTimeline.getTrips().get(0);
        Instant newEndTime = firstGeneratedTrip.getTimestamp().plus(Duration.ofMinutes(firstGeneratedTrip.getTripDuration()));
        long newDurationMinutes = Duration.between(lastSavedEvent.getStartTime(), newEndTime).toMinutes();

        log.debug("Updating existing trip {} with new duration: {} minutes", 
                 lastSavedEvent.getId(), newDurationMinutes);

        // This would update the database - for now just log
        // timelineTripRepository.updateEndTimeAndDuration(lastSavedEvent.getId(), newEndTime, newDurationMinutes);
    }

    /**
     * Update boundary event for multi-day processing.
     */
    private void updateBoundaryEvent(TimelineEventRetriever.TimelineEvent boundaryEvent, 
                                    MovementTimelineDTO timeline) {
        // Similar to the overnight algorithm but for multi-day ranges
        if (boundaryEvent.getType() == TimelineEventRetriever.TimelineEventType.STAY && timeline.getStaysCount() > 0) {
            // Remove first stay from timeline since we're extending the existing one
            timeline.getStays().remove(0);
        }
        // Similar logic for trips if needed
    }

    /**
     * Create timeline with a single data gap for the entire period.
     */
    private MovementTimelineDTO createTimelineWithDataGap(UUID userId, Instant startTime, Instant endTime) {
        MovementTimelineDTO timeline = new MovementTimelineDTO(userId);
        timeline.setDataSource(TimelineDataSource.CACHED);
        timeline.setLastUpdated(Instant.now());
        
        // Add data gap for the entire period
        TimelineDataGapDTO dataGap = new TimelineDataGapDTO(startTime, endTime);
        timeline.getDataGaps().add(dataGap);
        
        // Persist the data gap
        persistDataGap(userId, startTime, endTime);
        
        log.debug("Created timeline with data gap from {} to {}", startTime, endTime);
        
        return timeline;
    }

    /**
     * Persist data gap to database.
     */
    private void persistDataGap(UUID userId, Instant startTime, Instant endTime) {
        userRepository.findByIdOptional(userId).ifPresent(user -> {
            TimelineDataGapEntity dataGapEntity = TimelineDataGapEntity.builder()
                .user(user)
                .startTime(startTime)
                .endTime(endTime)
                .build();
            timelineDataGapRepository.persist(dataGapEntity);
            log.debug("Persisted data gap entity: {} to {}", startTime, endTime);
        });
    }
}