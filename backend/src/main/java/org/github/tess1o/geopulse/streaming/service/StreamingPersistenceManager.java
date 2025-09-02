package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineDataGapDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.service.converters.StreamingTimelineConverter;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.util.UUID;

/**
 * Manages persistence of streaming timeline results to existing database tables.
 * Converts MovementTimelineDTO components to entities and performs batch persistence.
 */
@ApplicationScoped
@Slf4j
public class StreamingPersistenceManager {

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    TimelineDataGapRepository gapRepository;

    @Inject
    StreamingTimelineConverter converter;

    /**
     * Persist timeline data to database using injected repositories.
     *
     * @param userId   user identifier
     * @param timeline timeline data to persist
     */
    public void persistTimeline(UUID userId, MovementTimelineDTO timeline) {

        log.debug("Starting timeline persistence for user {}: {} stays, {} trips, {} gaps",
                userId, timeline.getStaysCount(), timeline.getTripsCount(), timeline.getDataGapsCount());

        // Create user reference for entities
        UserEntity userRef = converter.createUserReference(userId);

        // Persist stays
        if (timeline.getStays() != null && !timeline.getStays().isEmpty()) {
            persistStays(timeline.getStays(), userRef);
        }

        // Persist trips
        if (timeline.getTrips() != null && !timeline.getTrips().isEmpty()) {
            persistTrips(timeline.getTrips(), userRef);
        }

        // Persist data gaps
        if (timeline.getDataGaps() != null && !timeline.getDataGaps().isEmpty()) {
            persistDataGaps(timeline.getDataGaps(), userRef);
        }

        log.debug("Timeline persistence completed for user {}", userId);
    }

    /**
     * Persist stay locations to database.
     */
    private void persistStays(Iterable<TimelineStayLocationDTO> stays, UserEntity userRef) {
        int stayCount = 0;
        for (TimelineStayLocationDTO stay : stays) {
            TimelineStayEntity entity = converter.convertStayToEntity(stay, userRef);
            if (entity != null) {
                stayRepository.persist(entity);
                stayCount++;
            }
        }
        log.debug("Persisted {} stay entities", stayCount);
    }

    /**
     * Persist trips to database.
     */
    private void persistTrips(Iterable<TimelineTripDTO> trips, UserEntity userRef) {
        int tripCount = 0;
        for (TimelineTripDTO trip : trips) {
            TimelineTripEntity entity = converter.convertTripToEntity(trip, userRef);
            if (entity != null) {
                tripRepository.persist(entity);
                tripCount++;
            }
        }
        log.debug("Persisted {} trip entities", tripCount);
    }

    /**
     * Persist data gaps to database.
     */
    private void persistDataGaps(Iterable<TimelineDataGapDTO> gaps, UserEntity userRef) {
        int gapCount = 0;
        for (TimelineDataGapDTO gap : gaps) {
            TimelineDataGapEntity entity = converter.convertGapToEntity(gap, userRef);
            if (entity != null) {
                gapRepository.persist(entity);
                gapCount++;
            }
        }
        log.debug("Persisted {} data gap entities", gapCount);
    }
}