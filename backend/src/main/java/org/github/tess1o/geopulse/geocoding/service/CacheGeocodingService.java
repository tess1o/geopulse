package org.github.tess1o.geopulse.geocoding.service;

import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.geocoding.exception.GeocodingCacheException;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ultra-simplified geocoding service that stores only formatted display names.
 * No complex address components - just coordinates, bounding box, and final display string.
 * Works with adapters to support multiple providers.
 */
@ApplicationScoped
@Slf4j
public class CacheGeocodingService {

    private final ReverseGeocodingLocationRepository repository;

    @ConfigProperty(name = "geocoding.cache.spatial-tolerance-meters", defaultValue = "25")
    @StaticInitSafe
    double spatialToleranceMeters;

    @Inject
    public CacheGeocodingService(ReverseGeocodingLocationRepository repository) {
        this.repository = repository;
    }

    /**
     * Get cached result for the given coordinates as a FormattableGeocodingResult.
     * Prioritizes user-specific copies over originals.
     *
     * @param userId The user ID to filter by
     * @param requestCoordinates The coordinates to look up
     * @return Full geocoding result if found
     */
    @Transactional(TxType.REQUIRES_NEW)
    public Optional<FormattableGeocodingResult> getCachedGeocodingResult(UUID userId, Point requestCoordinates) {
        if (requestCoordinates == null) {
            return Optional.empty();
        }

        try {
            ReverseGeocodingLocationEntity match = repository.findByRequestCoordinates(
                    userId, requestCoordinates, spatialToleranceMeters
            );

            if (match != null) {
                log.debug("Cache hit for user {} at coordinates: lon={}, lat={} (tolerance: {}m), provider: {}, isUserSpecific: {}",
                        userId, requestCoordinates.getX(), requestCoordinates.getY(), spatialToleranceMeters,
                        match.getProviderName(), match.getUser() != null);
                
                // Convert entity back to FormattableGeocodingResult
                FormattableGeocodingResult result = SimpleFormattableResult.builder()
                        .requestCoordinates(match.getRequestCoordinates())
                        .resultCoordinates(match.getResultCoordinates())
                        .boundingBox(match.getBoundingBox())
                        .formattedDisplayName(match.getDisplayName())
                        .providerName(match.getProviderName())
                        .city(match.getCity())
                        .country(match.getCountry())
                        .build();
                
                return Optional.of(result);
            }

            log.debug("Cache miss for user {} at coordinates: lon={}, lat={}",
                    userId, requestCoordinates.getX(), requestCoordinates.getY());
            return Optional.empty();

        } catch (Exception e) {
            log.error("Error retrieving cached result for user {} at coordinates: lon={}, lat={}",
                    userId, requestCoordinates.getX(), requestCoordinates.getY(), e);
            throw new GeocodingCacheException("Failed to retrieve cached result", e);
        }
    }

    /**
     * Get the entity ID for a cached geocoding result.
     * Prioritizes user-specific copies over originals.
     *
     * @param userId The user ID to filter by
     * @param requestCoordinates The coordinates to look up
     * @return Entity ID if found in cache
     */
    @Transactional(TxType.REQUIRES_NEW)
    public Optional<Long> getCachedGeocodingResultId(UUID userId, Point requestCoordinates) {
        if (requestCoordinates == null) {
            return Optional.empty();
        }

        try {
            ReverseGeocodingLocationEntity match = repository.findByRequestCoordinates(
                    userId, requestCoordinates, spatialToleranceMeters
            );

            if (match != null) {
                log.debug("Found cached entity ID {} for user {} at coordinates: lon={}, lat={}, isUserSpecific: {}",
                        match.getId(), userId, requestCoordinates.getX(), requestCoordinates.getY(),
                        match.getUser() != null);
                return Optional.of(match.getId());
            }

            return Optional.empty();

        } catch (Exception e) {
            log.error("Error retrieving cached entity ID for user {} at coordinates: lon={}, lat={}",
                    userId, requestCoordinates.getX(), requestCoordinates.getY(), e);
            return Optional.empty();
        }
    }

    /**
     * Cache a structured geocoding result.
     * CRITICAL: Always saves as original (user_id = NULL) for shared cache.
     * Uses REQUIRES_NEW to ensure cache writes commit independently and survive outer transaction rollbacks.
     * This prevents the "going in circles" problem where successful geocoding results are lost on rollback.
     *
     * RACE CONDITION HANDLING:
     * Due to REQUIRES_NEW transaction isolation, this method cannot see uncommitted data from parent transaction.
     * This can cause findOriginalByExactCoordinates() to return null even when an original exists (but uncommitted).
     * The database unique constraint (idx_reverse_geocoding_unique_original_coords) will catch duplicate inserts.
     * We handle the constraint violation by querying again and updating the existing entry.
     *
     * @param geocodingResult The structured geocoding result to cache
     */
    @Transactional(TxType.REQUIRES_NEW)
    public void cacheGeocodingResult(FormattableGeocodingResult geocodingResult) {
        if (geocodingResult == null || geocodingResult.getRequestCoordinates() == null) {
            throw new IllegalArgumentException("Geocoding result and request coordinates cannot be null");
        }

        Point requestCoordinates = geocodingResult.getRequestCoordinates();

        try {
            // Convert to entity and save
            ReverseGeocodingLocationEntity entity = convertToEntity(geocodingResult);
            // CRITICAL: Always save as original (user_id = NULL) for shared cache
            entity.setUser(null);

            // Check if we already have this exact location (original only)
            // CRITICAL: findOriginalByExactCoordinates() only returns originals (user_id IS NULL)
            // This prevents duplicate originals when user copies exist at same coordinates
            //
            // NOTE: Due to REQUIRES_NEW isolation, this may return null even if original exists
            // (but is uncommitted in parent transaction). The unique constraint will catch this.
            ReverseGeocodingLocationEntity existing = repository.findOriginalByExactCoordinates(requestCoordinates);
            if (existing != null) {
                // Update existing original entry (we know existing.getUser() == null)
                updateExistingOriginal(existing, entity);
                log.debug("Updated cached original for coordinates: lon={}, lat={}",
                        requestCoordinates.getX(), requestCoordinates.getY());
            } else {
                // Create new original entry
                Instant now = Instant.now();
                entity.setCreatedAt(now);
                entity.setLastAccessedAt(now);

                try {
                    repository.persist(entity);
                    log.debug("Cached new original for coordinates: lon={}, lat={}",
                            requestCoordinates.getX(), requestCoordinates.getY());
                } catch (jakarta.persistence.PersistenceException e) {
                    // Check if this is a unique constraint violation
                    if (isUniqueConstraintViolation(e)) {
                        // Another transaction created the original while we were working
                        // Query again to get the existing entry and update it
                        log.debug("Unique constraint violation - original was created concurrently. " +
                                "Querying again and updating for coordinates: lon={}, lat={}",
                                requestCoordinates.getX(), requestCoordinates.getY());

                        ReverseGeocodingLocationEntity existingAfterRetry =
                            repository.findOriginalByExactCoordinates(requestCoordinates);

                        if (existingAfterRetry != null) {
                            updateExistingOriginal(existingAfterRetry, entity);
                            log.debug("Updated existing original after constraint violation for coordinates: lon={}, lat={}",
                                    requestCoordinates.getX(), requestCoordinates.getY());
                        } else {
                            // Should never happen - we got constraint violation but can't find the entry
                            log.error("Unique constraint violation but cannot find existing original for coordinates: lon={}, lat={}",
                                    requestCoordinates.getX(), requestCoordinates.getY());
                            throw new GeocodingCacheException("Constraint violation but existing entry not found", e);
                        }
                    } else {
                        // Some other persistence exception
                        throw e;
                    }
                }
            }

        } catch (GeocodingCacheException e) {
            // Re-throw our own exceptions
            throw e;
        } catch (Exception e) {
            log.error("Error caching geocoding result for coordinates: lon={}, lat={}",
                    requestCoordinates.getX(), requestCoordinates.getY(), e);
            throw new GeocodingCacheException("Failed to cache geocoding result", e);
        }
    }

    /**
     * Update an existing original entry with new data.
     */
    private void updateExistingOriginal(ReverseGeocodingLocationEntity existing, ReverseGeocodingLocationEntity newData) {
        existing.setResultCoordinates(newData.getResultCoordinates());
        existing.setBoundingBox(newData.getBoundingBox());
        existing.setDisplayName(newData.getDisplayName());
        existing.setProviderName(newData.getProviderName());
        existing.setCity(newData.getCity());
        existing.setCountry(newData.getCountry());
        existing.setLastAccessedAt(Instant.now());
        repository.persist(existing);
    }

    /**
     * Check if the exception is a unique constraint violation.
     */
    private boolean isUniqueConstraintViolation(Exception e) {
        // Check for PostgreSQL unique violation error code 23505
        String message = e.getMessage();
        if (message == null) {
            return false;
        }

        return message.contains("unique constraint")
            || message.contains("idx_reverse_geocoding_unique_original_coords")
            || message.contains("duplicate key value");
    }

    /**
     * Batch get cached geocoding result IDs for multiple coordinates.
     * Optimized for timeline assembly to reduce database round-trips.
     * Prioritizes user-specific copies over originals.
     *
     * @param userId The user ID to filter by
     * @param coordinates List of coordinates to look up
     * @return Map of coordinate string (lon,lat) to entity ID
     */
    @Transactional(TxType.REQUIRES_NEW)
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

        } catch (jakarta.persistence.QueryTimeoutException e) {
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
     * Optimized for timeline assembly to reduce database round-trips.
     * Prioritizes user-specific copies over originals.
     *
     * @param userId The user ID to filter by
     * @param coordinates List of coordinates to look up
     * @return Map of coordinate string (lon,lat) to FormattableGeocodingResult
     */
    @Transactional(TxType.REQUIRES_NEW)
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
                            entry -> convertFromEntity(entry.getValue())
                    ));

        } catch (jakarta.persistence.QueryTimeoutException e) {
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

    /**
     * Convert entity to FormattableGeocodingResult.
     */
    private FormattableGeocodingResult convertFromEntity(ReverseGeocodingLocationEntity entity) {
        return SimpleFormattableResult.builder()
                .requestCoordinates(entity.getRequestCoordinates())
                .resultCoordinates(entity.getResultCoordinates())
                .boundingBox(entity.getBoundingBox())
                .formattedDisplayName(entity.getDisplayName())
                .providerName(entity.getProviderName())
                .city(entity.getCity())
                .country(entity.getCountry())
                .build();
    }

    /**
     * Convert FormattableGeocodingResult to entity.
     */
    private ReverseGeocodingLocationEntity convertToEntity(FormattableGeocodingResult result) {
        ReverseGeocodingLocationEntity entity = new ReverseGeocodingLocationEntity();
        entity.setRequestCoordinates(result.getRequestCoordinates());
        entity.setResultCoordinates(result.getResultCoordinates());
        entity.setBoundingBox(result.getBoundingBox());
        entity.setDisplayName(result.getFormattedDisplayName());
        entity.setProviderName(result.getProviderName());
        entity.setCity(result.getCity());
        entity.setCountry(result.getCountry());
        return entity;
    }
}