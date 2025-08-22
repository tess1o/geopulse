package org.github.tess1o.geopulse.timeline.service.redesign;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineDataGapDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineDataSource;
import org.github.tess1o.geopulse.timeline.model.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineTripDTO;
import org.github.tess1o.geopulse.timeline.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.timeline.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.timeline.mapper.TimelinePersistenceMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Clean database access layer for timeline events.
 * Handles querying stays, trips, and data gaps with boundary expansion support.
 */
@ApplicationScoped
@Slf4j
public class TimelineEventRetriever {

    @Inject
    TimelineStayRepository timelineStayRepository;
    
    @Inject
    TimelineTripRepository timelineTripRepository;
    
    @Inject
    TimelineDataGapRepository timelineDataGapRepository;
    
    @Inject
    TimelinePersistenceMapper persistenceMapper;

    /**
     * Check if complete timeline data exists in cache for the given time range.
     */
    public boolean hasCompleteData(UUID userId, Instant startTime, Instant endTime) {
        log.debug("Checking for complete timeline data for user {} from {} to {}", userId, startTime, endTime);
        
        // Check if we have any timeline events (stays, trips, or data gaps) covering this period
        boolean hasStays = !timelineStayRepository.findByUserIdAndTimeRange(userId, startTime, endTime).isEmpty();
        boolean hasTrips = !timelineTripRepository.findByUserIdAndTimeRange(userId, startTime, endTime).isEmpty();
        boolean hasDataGaps = !timelineDataGapRepository.findByUserIdAndTimeRange(userId, startTime, endTime).isEmpty();
        
        boolean hasData = hasStays || hasTrips || hasDataGaps;
        log.debug("Complete data check result: {} (stays: {}, trips: {}, gaps: {})", 
                 hasData, hasStays, hasTrips, hasDataGaps);
        
        return hasData;
    }

    /**
     * Get existing timeline events from database for the given time range.
     * Includes boundary expansion - events that start before the range but extend into it.
     */
    public MovementTimelineDTO getExistingEvents(UUID userId, Instant startTime, Instant endTime) {
        log.debug("Retrieving existing timeline events for user {} from {} to {}", userId, startTime, endTime);
        
        MovementTimelineDTO timeline = new MovementTimelineDTO(userId);
        timeline.setDataSource(TimelineDataSource.CACHED);
        timeline.setLastUpdated(Instant.now());
        
        // Get stays with boundary expansion
        var stayEntities = timelineStayRepository.findByUserIdAndTimeRangeWithExpansion(userId, startTime, endTime);
        for (var stayEntity : stayEntities) {
            TimelineStayLocationDTO stayDTO = persistenceMapper.toDTO(stayEntity);
            timeline.getStays().add(stayDTO);
        }
        
        // Get trips with boundary expansion
        var tripEntities = timelineTripRepository.findByUserIdAndTimeRangeWithExpansion(userId, startTime, endTime);
        for (var tripEntity : tripEntities) {
            TimelineTripDTO tripDTO = persistenceMapper.toTripDTO(tripEntity);
            timeline.getTrips().add(tripDTO);
        }
        
        // Get data gaps with boundary expansion
        var gapEntities = timelineDataGapRepository.findByUserIdAndTimeRangeWithExpansion(userId, startTime, endTime);
        for (var gapEntity : gapEntities) {
            TimelineDataGapDTO gapDTO = new TimelineDataGapDTO(gapEntity.getStartTime(), gapEntity.getEndTime());
            timeline.getDataGaps().add(gapDTO);
        }
        
        log.debug("Retrieved {} stays, {} trips, {} data gaps", 
                 timeline.getStaysCount(), timeline.getTripsCount(), timeline.getDataGapsCount());
        
        return timeline;
    }

    /**
     * Find the most recent event (stay, trip, or data gap) before the given timestamp.
     * Used for previous context prepending and boundary expansion.
     */
    public Optional<TimelineEvent> findLatestEventBefore(UUID userId, Instant beforeTimestamp) {
        log.debug("Finding latest event before {} for user {}", beforeTimestamp, userId);
        
        // Find latest stay
        var latestStay = timelineStayRepository.findLatestBefore(userId, beforeTimestamp);
        
        // Find latest trip
        var latestTrip = timelineTripRepository.findLatestBefore(userId, beforeTimestamp);
        
        // Find latest data gap
        var latestDataGap = timelineDataGapRepository.findLatestBefore(userId, beforeTimestamp);
        
        // Determine which is most recent
        TimelineEvent mostRecentEvent = null;
        Instant mostRecentTime = null;
        
        if (latestStay != null) {
            mostRecentEvent = new TimelineEvent(TimelineEventType.STAY, latestStay.getId(), 
                                              latestStay.getTimestamp(), latestStay.getStayDuration());
            mostRecentTime = latestStay.getTimestamp().plusSeconds(latestStay.getStayDuration());
        }
        
        if (latestTrip != null) {
            Instant tripEndTime = latestTrip.getTimestamp().plusSeconds(latestTrip.getTripDuration() * 60L);
            if (mostRecentTime == null || tripEndTime.isAfter(mostRecentTime)) {
                mostRecentEvent = new TimelineEvent(TimelineEventType.TRIP, latestTrip.getId(),
                                                  latestTrip.getTimestamp(), latestTrip.getTripDuration() * 60L);
                mostRecentTime = tripEndTime;
            }
        }
        
        if (latestDataGap != null) {
            Instant gapEndTime = latestDataGap.getStartTime().plusSeconds(latestDataGap.getDurationSeconds());
            if (mostRecentTime == null || gapEndTime.isAfter(mostRecentTime)) {
                mostRecentEvent = new TimelineEvent(TimelineEventType.DATA_GAP, latestDataGap.getId(),
                                                  latestDataGap.getStartTime(), latestDataGap.getDurationSeconds());
                mostRecentTime = gapEndTime;
            }
        }
        
        if (mostRecentEvent != null) {
            log.debug("Found latest event: {} at {}", mostRecentEvent.getType(), mostRecentEvent.getStartTime());
        } else {
            log.debug("No events found before {}", beforeTimestamp);
        }
        
        return Optional.ofNullable(mostRecentEvent);
    }
    
    /**
     * Delete all timeline events for a specific time range.
     * Used when regenerating timeline data from scratch.
     */
    public void deleteTimelineData(UUID userId, Instant startTime, Instant endTime) {
        log.debug("Deleting timeline data for user {} from {} to {}", userId, startTime, endTime);
        
        // Delete in proper order to handle foreign key constraints
        long deletedDataGaps = timelineDataGapRepository.deleteByUserIdAndTimeRange(userId, startTime, endTime);
        long deletedStays = timelineStayRepository.deleteByUserIdAndTimeRange(userId, startTime, endTime);
        long deletedTrips = timelineTripRepository.deleteByUserIdAndTimeRange(userId, startTime, endTime);
        
        log.debug("Deleted {} data gaps, {} stays, {} trips", deletedDataGaps, deletedStays, deletedTrips);
    }

    /**
     * Represents a timeline event (stay, trip, or data gap) for boundary expansion logic.
     */
    public static class TimelineEvent {
        private final TimelineEventType type;
        private final Long id;
        private final Instant startTime;
        private final Long durationSeconds;
        
        public TimelineEvent(TimelineEventType type, Long id, Instant startTime, Long durationSeconds) {
            this.type = type;
            this.id = id;
            this.startTime = startTime;
            this.durationSeconds = durationSeconds;
        }
        
        public TimelineEventType getType() { return type; }
        public Long getId() { return id; }
        public Instant getStartTime() { return startTime; }
        public Long getDurationSeconds() { return durationSeconds; }
        
        public Instant getEndTime() {
            return startTime.plusSeconds(durationSeconds);
        }
        
        public boolean extendsInto(Instant timestamp) {
            return getEndTime().isAfter(timestamp);
        }
    }
    
    public enum TimelineEventType {
        STAY, TRIP, DATA_GAP
    }
}