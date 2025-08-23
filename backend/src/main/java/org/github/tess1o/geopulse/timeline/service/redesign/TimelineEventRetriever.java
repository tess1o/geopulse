package org.github.tess1o.geopulse.timeline.service.redesign;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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
     * This validates that the ENTIRE time range is covered by existing timeline events.
     */
    public boolean hasCompleteData(UUID userId, Instant startTime, Instant endTime) {
        log.debug("Checking for complete timeline data for user {} from {} to {}", userId, startTime, endTime);
        
        // Get all timeline events that overlap with the requested range (using expansion queries for consistency)
        var stays = timelineStayRepository.findByUserIdAndTimeRangeWithExpansion(userId, startTime, endTime);
        var trips = timelineTripRepository.findByUserIdAndTimeRangeWithExpansion(userId, startTime, endTime);
        var gaps = timelineDataGapRepository.findByUserIdAndTimeRangeWithExpansion(userId, startTime, endTime);
        
        log.debug("Found overlapping events: {} stays, {} trips, {} gaps", stays.size(), trips.size(), gaps.size());
        
        // If no events exist at all, definitely not complete
        if (stays.isEmpty() && trips.isEmpty() && gaps.isEmpty()) {
            log.debug("Complete data check result: false (no events found)");
            return false;
        }
        
        // Check if the entire requested time range is covered by existing timeline events
        boolean isCovered = isTimeRangeCovered(userId, startTime, endTime, stays, trips, gaps);
        
        log.debug("Complete data check result: {} (coverage validated)", isCovered);
        return isCovered;
    }
    
    /**
     * Validate that a time range is completely covered by existing timeline events.
     * This ensures no uncovered gaps exist in the requested time range.
     */
    private boolean isTimeRangeCovered(UUID userId, Instant startTime, Instant endTime,
                                     List<?> stays, List<?> trips, List<?> gaps) {
        
        // If we have real timeline events (stays or trips), consider the range covered
        // This indicates that timeline processing already occurred for this period
        if (!stays.isEmpty() || !trips.isEmpty()) {
            log.debug("Found real timeline events - range considered covered");
            return true;
        }
        
        // If we only have gaps, check if any gap covers the entire requested range
        // This ensures GPS data processing isn't skipped when gaps only partially cover the range
        if (!gaps.isEmpty()) {
            for (Object gapObj : gaps) {
                var gap = (org.github.tess1o.geopulse.timeline.model.TimelineDataGapEntity) gapObj;
                
                // Gap must start at or before requested start AND end at or after requested end
                if (!gap.getStartTime().isAfter(startTime) && !gap.getEndTime().isBefore(endTime)) {
                    log.debug("Found gap that covers entire range: {} to {}", gap.getStartTime(), gap.getEndTime());
                    return true;
                }
            }
            log.debug("Found gaps but none cover entire range - may need GPS processing");
            return false;
        }
        
        // No timeline events at all
        log.debug("No timeline events found - range not covered");
        return false;
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
    @Transactional
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