package org.github.tess1o.geopulse.geocoding.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.QueryTimeoutException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.geocoding.mapper.GeocodingEntityMapper;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.locationtech.jts.geom.Point;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for batch geocoding cache operations.
 * Optimized for timeline assembly to reduce database round-trips.
 */
@ApplicationScoped
@Slf4j
public class CacheGeocodingBatchService {

    private final ReverseGeocodingLocationRepository repository;
    private final GeocodingEntityMapper entityMapper;

    @ConfigProperty(name = "geocoding.cache.spatial-tolerance-meters", defaultValue = "25")
    double spatialToleranceMeters;

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
        if (coordinates == null || coordinates.isEmpty()) {
            return Map.of();
        }

        long startTime = System.currentTimeMillis();
        try {
            log.debug("Starting batch geocoding ID lookup for user {} with {} coordinates", userId, coordinates.size());

            Map<String, ReverseGeocodingLocationEntity> cachedResults =
                    repository.findByCoordinatesBatchReal(userId, coordinates, spatialToleranceMeters);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Batch geocoding ID lookup completed in {}ms for user {} with {} coordinates ({} cached hits)",
                    duration, userId, coordinates.size(), cachedResults.size());

            return cachedResults.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().getId()
                    ));

        } catch (QueryTimeoutException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("Batch geocoding ID lookup timed out after {}ms for user {} with {} coordinates. " +
                            "Returning empty result - locations will need individual geocoding.",
                    duration, userId, coordinates.size());
            return Map.of();
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Error retrieving batch cached entity IDs for user {} with {} coordinates after {}ms",
                    userId, coordinates.size(), duration, e);
            return Map.of();
        }
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
        if (coordinates == null || coordinates.isEmpty()) {
            return Map.of();
        }

        long startTime = System.currentTimeMillis();
        try {
            log.debug("Starting batch geocoding results lookup for user {} with {} coordinates", userId, coordinates.size());

            Map<String, ReverseGeocodingLocationEntity> cachedResults =
                    repository.findByCoordinatesBatchReal(userId, coordinates, spatialToleranceMeters);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Batch geocoding results lookup completed in {}ms for user {} with {} coordinates ({} cached hits)",
                    duration, userId, coordinates.size(), cachedResults.size());

            return cachedResults.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entityMapper.toResult(entry.getValue())
                    ));

        } catch (QueryTimeoutException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("Batch geocoding results lookup timed out after {}ms for user {} with {} coordinates. " +
                            "Returning empty result - locations will need individual geocoding.",
                    duration, userId, coordinates.size());
            return Map.of();
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Error retrieving batch cached results for user {} with {} coordinates after {}ms",
                    userId, coordinates.size(), duration, e);
            return Map.of();
        }
    }
}
