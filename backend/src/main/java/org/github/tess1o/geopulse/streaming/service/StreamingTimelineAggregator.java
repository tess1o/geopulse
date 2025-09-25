package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.ai.model.AIMovementTimelineDTO;
import org.github.tess1o.geopulse.ai.model.AITimelineStayDTO;
import org.github.tess1o.geopulse.ai.model.AITimelineTripDTO;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineDataGapDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.service.converters.StreamingTimelineConverter;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class StreamingTimelineAggregator {

    @Inject
    TimelineStayRepository timelineStayRepository;

    @Inject
    TimelineTripRepository timelineTripRepository;

    @Inject
    TimelineDataGapRepository timelineDataGapRepository;

    @Inject
    StreamingTimelineConverter converter;

    public MovementTimelineDTO getTimelineFromDb(UUID userId, Instant startTime, Instant endTime) {
        return getExistingTimelineEvents(userId, startTime, endTime);
    }

    /**
     * Get AI-optimized timeline data with enriched location information.
     * Includes city/country data from joins and trip origin/destination names.
     * 
     * @param userId user ID
     * @param startTime start of time range
     * @param endTime end of time range
     * @return AI-optimized timeline with enriched data
     */
    public AIMovementTimelineDTO getTimelineForAI(UUID userId, Instant startTime, Instant endTime) {
        log.debug("Retrieving AI timeline events for user {} from {} to {}", userId, startTime, endTime);

        AIMovementTimelineDTO timeline = new AIMovementTimelineDTO(userId);
        timeline.setLastUpdated(Instant.now());

        // Get stays with city/country information via SQL joins
        var aiStays = timelineStayRepository.findAITimelineStaysWithLocationData(userId, startTime, endTime);
        timeline.setStays(aiStays);

        // Get trips without GPS path data
        var aiTrips = timelineTripRepository.findAITimelineTripsWithoutPath(userId, startTime, endTime);
        
        // Populate origin/destination information for trips
        populateOriginDestination(aiTrips, aiStays);
        
        timeline.setTrips(aiTrips);

        log.debug("Retrieved {} AI stays, {} AI trips", timeline.getStaysCount(), timeline.getTripsCount());

        return timeline;
    }

    /**
     * Populate origin and destination location names for trips based on nearby stays.
     * Origin: stay that ended closest to (but before) the trip start
     * Destination: stay that started closest to (but after) the trip end
     * 
     * @param aiTrips list of AI trips to populate
     * @param aiStays list of AI stays to use for origin/destination lookup
     */
    private void populateOriginDestination(java.util.List<AITimelineTripDTO> aiTrips, java.util.List<AITimelineStayDTO> aiStays) {
        for (AITimelineTripDTO trip : aiTrips) {
            Instant tripStart = trip.getTimestamp();
            Instant tripEnd = tripStart.plusSeconds(trip.getTripDuration());

            // Find origin: stay that ended closest to (but before) trip start
            AITimelineStayDTO origin = null;
            long minOriginGap = Long.MAX_VALUE;
            
            for (AITimelineStayDTO stay : aiStays) {
                Instant stayEnd = stay.getTimestamp().plusSeconds(stay.getStayDurationSeconds());
                if (stayEnd.isBefore(tripStart) || stayEnd.equals(tripStart)) {
                    long gap = java.time.Duration.between(stayEnd, tripStart).toSeconds();
                    if (gap < minOriginGap) {
                        minOriginGap = gap;
                        origin = stay;
                    }
                }
            }

            // Find destination: stay that started closest to (but after) trip end
            AITimelineStayDTO destination = null;
            long minDestinationGap = Long.MAX_VALUE;
            
            for (AITimelineStayDTO stay : aiStays) {
                Instant stayStart = stay.getTimestamp();
                if (stayStart.isAfter(tripEnd) || stayStart.equals(tripEnd)) {
                    long gap = java.time.Duration.between(tripEnd, stayStart).toSeconds();
                    if (gap < minDestinationGap) {
                        minDestinationGap = gap;
                        destination = stay;
                    }
                }
            }

            // Set origin and destination names
            if (origin != null) {
                trip.setOriginLocationName(origin.getLocationName());
            }
            if (destination != null) {
                trip.setDestinationLocationName(destination.getLocationName());
            }
        }
    }

    private MovementTimelineDTO getExistingTimelineEvents(UUID userId, Instant startTime, Instant endTime) {
        log.debug("Retrieving existing timeline events for user {} from {} to {}", userId, startTime, endTime);

        MovementTimelineDTO timeline = new MovementTimelineDTO(userId);
        timeline.setLastUpdated(Instant.now());

        // Get stays with boundary expansion
        var stayEntities = timelineStayRepository.findByUserIdAndTimeRangeWithExpansion(userId, startTime, endTime);
        for (var stayEntity : stayEntities) {
            TimelineStayLocationDTO stayDTO = converter.convertStayEntityToDto(stayEntity);
            timeline.getStays().add(stayDTO);
        }

        // Get trips with boundary expansion
        var tripEntities = timelineTripRepository.findByUserIdAndTimeRangeWithExpansion(userId, startTime, endTime);
        for (var tripEntity : tripEntities) {
            TimelineTripDTO tripDTO = converter.convertTripEntityToDto(tripEntity);
            timeline.getTrips().add(tripDTO);
        }

        // Get data gaps with boundary expansion
        var gapEntities = timelineDataGapRepository.findByUserIdAndTimeRangeWithExpansion(userId, startTime, endTime);
        for (var gapEntity : gapEntities) {
            var gapDTO = new TimelineDataGapDTO(
                    gapEntity.getStartTime(), gapEntity.getEndTime());
            timeline.getDataGaps().add(gapDTO);
        }

        log.debug("Retrieved {} stays, {} trips, {} data gaps",
                timeline.getStaysCount(), timeline.getTripsCount(), timeline.getDataGapsCount());

        return timeline;
    }
}
