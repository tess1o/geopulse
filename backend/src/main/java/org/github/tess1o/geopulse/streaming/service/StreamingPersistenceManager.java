package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.streaming.engine.TimelineEventFinalizationService;
import org.github.tess1o.geopulse.streaming.model.domain.DataGap;
import org.github.tess1o.geopulse.streaming.model.domain.RawTimeline;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.service.converters.StreamingTimelineConverter;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Manages persistence of streaming timeline results to existing database tables.
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

    @Inject
    TimelineEventFinalizationService finalizationService;

    /**
     * Persist raw timeline with GPS statistics calculation.
     * This method processes trips with rich GPS data while maintaining optimized stays/gaps persistence.
     *
     * @param userId      user identifier
     * @param rawTimeline raw timeline container with domain objects
     */
    public void persistRawTimeline(UUID userId, RawTimeline rawTimeline) {
        persistRawTimeline(userId, rawTimeline, null);
    }

    /**
     * Persist raw timeline with GPS statistics calculation and progress tracking.
     * This method processes trips with rich GPS data while maintaining optimized stays/gaps persistence.
     *
     * @param userId      user identifier
     * @param rawTimeline raw timeline container with domain objects
     * @param jobId       optional job ID for progress tracking
     */
    public void persistRawTimeline(UUID userId, RawTimeline rawTimeline, UUID jobId) {
        log.debug("Starting raw timeline persistence for user {} with {} events",
                userId, rawTimeline.getTotalEventCount());

        // Create user reference for entities  
        UserEntity userRef = converter.createUserReference(userId);

        int tripCount = persistTripsWithGpsStats(rawTimeline.getTrips(), userRef);
        int stayCount = persistStaysFromDomain(rawTimeline.getStays(), userRef);
        int gapCount = persistDataGapsFromDomain(rawTimeline.getDataGaps(), userRef);

        log.debug("Raw timeline persistence completed for user {}: {} trips with GPS stats, {} stays, {} gaps",
                userId, tripCount, stayCount, gapCount);
    }

    /**
     * Persist trips with GPS statistics calculation from rich domain objects.
     */
    private int persistTripsWithGpsStats(List<Trip> trips, UserEntity userRef) {
        int tripCount = 0;
        for (Trip trip : trips) {
            TimelineTripEntity tripEntity = converter.convertStreamingTripToEntity(trip, userRef);
            if (tripEntity != null) {
                tripRepository.persist(tripEntity);
                tripCount++;
            }
        }
        log.debug("Persisted {} trip entities with GPS statistics", tripCount);
        return tripCount;
    }

    private int persistStaysFromDomain(List<Stay> stays, UserEntity userRef) {
        if (stays.isEmpty()) {
            return 0;
        }
        // Use existing optimized stay persistence with batch loading
        persistStays(stays, userRef);
        return stays.size();
    }

    private int persistDataGapsFromDomain(List<DataGap> dataGaps, UserEntity userRef) {
        if (dataGaps.isEmpty()) {
            return 0;
        }
        // Use existing data gap persistence
        persistDataGaps(dataGaps, userRef);
        return dataGaps.size();
    }

    /**
     * Persist stay locations to database using batch loading to eliminate N+1 queries.
     */
    private void persistStays(Iterable<Stay> stays, UserEntity userRef) {
        List<Stay> stayList = StreamSupport.stream(stays.spliterator(), false)
                .collect(Collectors.toList());

        if (stayList.isEmpty()) {
            return;
        }

        // Step 1: Collect all favorite and geocoding IDs
        Set<Long> favoriteIds = stayList.stream()
                .map(Stay::getFavoriteId)
                .filter(java.util.Objects::nonNull)
                .filter(id -> id != 0)
                .collect(java.util.stream.Collectors.toSet());

        Set<Long> geocodingIds = stayList.stream()
                .map(Stay::getGeocodingId)
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
        for (Stay stay : stayList) {
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
     * Persist data gaps to database.
     */
    private void persistDataGaps(Iterable<DataGap> gaps, UserEntity userRef) {
        int gapCount = 0;
        for (DataGap gap : gaps) {
            TimelineDataGapEntity entity = converter.convertGapToEntity(gap, userRef);
            if (entity != null) {
                gapRepository.persist(entity);
                gapCount++;
            }
        }
        log.debug("Persisted {} data gap entities", gapCount);
    }
}