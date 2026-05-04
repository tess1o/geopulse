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
                            getMaxBboxAreaSquareMeters()
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

    private double getMaxBboxAreaSquareMeters() {
        return Math.max(0d, maxBboxAreaKm2) * 1_000_000d;
    }
}
