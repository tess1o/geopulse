package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
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
