package org.github.tess1o.geopulse.geocoding.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.QueryTimeoutException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.geocoding.mapper.GeocodingEntityMapper;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.locationtech.jts.geom.Point;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for batch geocoding cache operations.
 * Optimized for timeline assembly to reduce database round-trips.
 */
@ApplicationScoped
@Slf4j
public class CacheGeocodingBatchService {

    public record BatchLookupResult(
            Map<String, FormattableGeocodingResult> results,
            Map<String, Long> ids
    ) {
    }

    private final ReverseGeocodingLocationRepository repository;
    private final GeocodingEntityMapper entityMapper;

    @ConfigProperty(name = "geocoding.cache.spatial-tolerance-meters", defaultValue = "25")
    double spatialToleranceMeters;

    @ConfigProperty(name = "geocoding.cache.max-bbox-area-km2", defaultValue = "5000")
    double maxBboxAreaKm2;

    @Inject
    SystemSettingsService settingsService;

    @Inject
    public CacheGeocodingBatchService(
            ReverseGeocodingLocationRepository repository,
            GeocodingEntityMapper entityMapper) {
        this.repository = repository;
        this.entityMapper = entityMapper;
    }

    /**
     * Batch get cached geocoding result IDs for multiple coordinates.
     * Prioritizes user-specific copies over originals.
     *
     * @param userId      The user ID to filter by
     * @param coordinates List of coordinates to look up
     * @return Map of coordinate string (lon,lat) to entity ID
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Map<String, Long> getCachedGeocodingResultIdsBatch(UUID userId, List<Point> coordinates) {
        return getCachedGeocodingResultsAndIdsBatch(userId, coordinates).ids();
    }

    /**
     * Batch get cached geocoding results for multiple coordinates.
     * Prioritizes user-specific copies over originals.
     *
     * @param userId      The user ID to filter by
     * @param coordinates List of coordinates to look up
     * @return Map of coordinate string (lon,lat) to FormattableGeocodingResult
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Map<String, FormattableGeocodingResult> getCachedGeocodingResultsBatch(UUID userId, List<Point> coordinates) {
        return getCachedGeocodingResultsAndIdsBatch(userId, coordinates).results();
    }

    /**
     * Batch get cached geocoding results and IDs in one DB lookup.
     * Use this for hot paths where both values are needed to avoid duplicate spatial queries.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public BatchLookupResult getCachedGeocodingResultsAndIdsBatch(UUID userId, List<Point> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            return new BatchLookupResult(Map.of(), Map.of());
        }

        long startTime = System.currentTimeMillis();
        try {
            log.debug("Starting batch geocoding results+ID lookup for user {} with {} coordinates", userId, coordinates.size());

            Map<String, ReverseGeocodingLocationEntity> cachedResults =
                    repository.findByCoordinatesBatchReal(
                            userId,
                            coordinates,
                            spatialToleranceMeters,
                            getMaxBboxAreaKm2()
                    );

            Map<String, FormattableGeocodingResult> resultMap = new HashMap<>(cachedResults.size());
            Map<String, Long> idMap = new HashMap<>(cachedResults.size());

            for (Map.Entry<String, ReverseGeocodingLocationEntity> entry : cachedResults.entrySet()) {
                ReverseGeocodingLocationEntity entity = entry.getValue();
                resultMap.put(entry.getKey(), entityMapper.toResult(entity));
                idMap.put(entry.getKey(), entity.getId());
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Batch geocoding results+ID lookup completed in {}ms for user {} with {} coordinates ({} cached hits)",
                    duration, userId, coordinates.size(), cachedResults.size());

            return new BatchLookupResult(Map.copyOf(resultMap), Map.copyOf(idMap));

        } catch (QueryTimeoutException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("Batch geocoding results+ID lookup timed out after {}ms for user {} with {} coordinates. " +
                            "Returning empty result - locations will need individual geocoding.",
                    duration, userId, coordinates.size());
            return new BatchLookupResult(Map.of(), Map.of());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Error retrieving batch cached results+IDs for user {} with {} coordinates after {}ms",
                    userId, coordinates.size(), duration, e);
            return new BatchLookupResult(Map.of(), Map.of());
        }
    }

    private double getMaxBboxAreaKm2() {
        double maxArea = settingsService == null
                ? maxBboxAreaKm2
                : settingsService.getInteger("geocoding.cache.max-bbox-area-km2");
        return Math.max(0d, maxArea);
    }

    /** SQLSTATE of a statement PostgreSQL cancelled, by its timeout among others. */
    private static final String QUERY_CANCELLED = "57014";

    /** How often a place lookup that timed out is retried as two halves before its coordinates are given up. */
    private static final int MAX_PLACE_LOOKUP_SPLITS = 3;

    /**
     * Result of {@link #getCachedPlacesBatch}: the cached place of each coordinate that has one, and the coordinates
     * whose lookup failed. A coordinate in neither is not cached.
     */
    public record CachedPlaces(Map<String, ReverseGeocodingLocationRepository.CachedPlace> places, List<Point> failed) {
    }

    /**
     * The cached place (id and display name) each coordinate resolves to, matched like
     * {@link #getCachedGeocodingResultsAndIdsBatch} but without loading entities: timeline
     * generation needs nothing else, and a full rebuild resolves tens of thousands of stays.
     * <p>
     * A lookup that times out is retried as two halves, at most {@value #MAX_PLACE_LOOKUP_SPLITS} times deep, so one
     * slow statement does not leave a whole chunk to individual lookups. Each attempt has a transaction of its own: a
     * statement that timed out aborts its transaction. Errors are logged and leave the coordinates in
     * {@link CachedPlaces#failed()}.
     */
    public CachedPlaces getCachedPlacesBatch(UUID userId, List<Point> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            return new CachedPlaces(Map.of(), List.of());
        }
        Map<String, ReverseGeocodingLocationRepository.CachedPlace> places = new HashMap<>();
        List<Point> failed = new ArrayList<>();
        lookUpPlaces(userId, coordinates, MAX_PLACE_LOOKUP_SPLITS, places, failed);
        return new CachedPlaces(places, failed);
    }

    private void lookUpPlaces(UUID userId, List<Point> coordinates, int splitsLeft,
                              Map<String, ReverseGeocodingLocationRepository.CachedPlace> places, List<Point> failed) {
        long startTime = System.currentTimeMillis();
        try {
            Map<String, ReverseGeocodingLocationRepository.CachedPlace> found = QuarkusTransaction.requiringNew()
                    .call(() -> repository.findPlacesByCoordinatesBatch(userId, coordinates, spatialToleranceMeters,
                            getMaxBboxAreaKm2()));
            log.info("Batch cached place lookup completed in {}ms for user {} with {} coordinates ({} cached hits)",
                    System.currentTimeMillis() - startTime, userId, coordinates.size(), found.size());
            places.putAll(found);
        } catch (RuntimeException e) {
            if (!isTimeout(e)) {
                log.error("Error retrieving batch cached places for user {} with {} coordinates after {}ms",
                        userId, coordinates.size(), System.currentTimeMillis() - startTime, e);
                failed.addAll(coordinates);
                return;
            }
            if (splitsLeft > 0 && coordinates.size() > 1) {
                log.warn("Batch cached place lookup timed out after {}ms for user {} with {} coordinates; "
                                + "retrying as two halves",
                        System.currentTimeMillis() - startTime, userId, coordinates.size());
                int half = coordinates.size() / 2;
                lookUpPlaces(userId, coordinates.subList(0, half), splitsLeft - 1, places, failed);
                lookUpPlaces(userId, coordinates.subList(half, coordinates.size()), splitsLeft - 1, places, failed);
                return;
            }
            log.warn("Batch cached place lookup timed out after {}ms for user {} with {} coordinates. " +
                            "Locations will need individual lookups.",
                    System.currentTimeMillis() - startTime, userId, coordinates.size());
            failed.addAll(coordinates);
        }
    }

    /**
     * Whether the lookup failed by timing out. PostgreSQL aborts the transaction of a cancelled statement, so
     * Hibernate reports the timeout as a plain PersistenceException with its QueryTimeoutException as the cause.
     */
    private static boolean isTimeout(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof QueryTimeoutException || cause instanceof org.hibernate.QueryTimeoutException
                    || cause instanceof SQLException sql && QUERY_CANCELLED.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
