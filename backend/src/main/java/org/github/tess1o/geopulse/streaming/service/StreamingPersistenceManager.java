package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
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

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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

    @Inject
    FavoritesRepository favoritesRepository;

    @Inject
    ReverseGeocodingLocationRepository geocodingRepository;

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
     * Persist stay locations to database using batch loading to eliminate N+1 queries.
     */
    private void persistStays(Iterable<TimelineStayLocationDTO> stays, UserEntity userRef) {
        java.util.List<TimelineStayLocationDTO> stayList = StreamSupport.stream(stays.spliterator(), false)
                .collect(Collectors.toList());

        if (stayList.isEmpty()) {
            return;
        }

        // Step 1: Collect all favorite and geocoding IDs
        Set<Long> favoriteIds = stayList.stream()
                .map(TimelineStayLocationDTO::getFavoriteId)
                .filter(java.util.Objects::nonNull)
                .filter(id -> id != 0)
                .collect(java.util.stream.Collectors.toSet());

        Set<Long> geocodingIds = stayList.stream()
                .map(TimelineStayLocationDTO::getGeocodingId)
                .filter(java.util.Objects::nonNull)
                .filter(id -> id != 0)
                .collect(java.util.stream.Collectors.toSet());

        // Step 2: Batch load all favorites and geocoding entities using repositories
        Map<Long, FavoritesEntity> favoriteMap =
                favoriteIds.isEmpty() ? java.util.Map.of() :
                        favoritesRepository.find("id in ?1", favoriteIds)
                                .list()
                                .stream()
                                .collect(Collectors.toMap(
                                        FavoritesEntity::getId,
                                        Function.identity()
                                ));

        Map<Long, ReverseGeocodingLocationEntity> geocodingMap =
                geocodingIds.isEmpty() ? java.util.Map.of() :
                        geocodingRepository.find("id in ?1", geocodingIds)
                                .list()
                                .stream()
                                .collect(Collectors.toMap(
                                        ReverseGeocodingLocationEntity::getId,
                                        Function.identity()
                                ));

        log.debug("Batch loaded {} favorites and {} geocoding entities for {} stays",
                favoriteMap.size(), geocodingMap.size(), stayList.size());

        // Step 3: Convert and persist stays using pre-loaded entity maps
        int stayCount = 0;
        for (TimelineStayLocationDTO stay : stayList) {
            TimelineStayEntity entity = converter.convertStayToEntityWithBatchData(stay, userRef, favoriteMap, geocodingMap);
            if (entity != null) {
                stayRepository.persist(entity);
                stayCount++;
            }
        }
        log.debug("Persisted {} stay entities using batch loading (reduced from 1+{}+{} to 3 queries)",
                stayCount, favoriteIds.size(), geocodingIds.size());
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