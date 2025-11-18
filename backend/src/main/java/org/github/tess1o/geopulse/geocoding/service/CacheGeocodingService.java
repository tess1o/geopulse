package org.github.tess1o.geopulse.geocoding.service;

import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.geocoding.exception.GeocodingCacheException;
import org.github.tess1o.geopulse.geocoding.mapper.GeocodingEntityMapper;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Ultra-simplified geocoding service that stores only formatted display names.
 * No complex address components - just coordinates, bounding box, and final display string.
 * Works with adapters to support multiple providers.
 */
@ApplicationScoped
@Slf4j
public class CacheGeocodingService {

    private final ReverseGeocodingLocationRepository repository;
    private final GeocodingEntityMapper entityMapper;

    @ConfigProperty(name = "geocoding.cache.spatial-tolerance-meters", defaultValue = "25")
    @StaticInitSafe
    double spatialToleranceMeters;

    @Inject
    public CacheGeocodingService(
            ReverseGeocodingLocationRepository repository,
            GeocodingEntityMapper entityMapper) {
        this.repository = repository;
        this.entityMapper = entityMapper;
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

                return Optional.of(entityMapper.toResult(match));
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
            ReverseGeocodingLocationEntity entity = entityMapper.toEntity(geocodingResult);
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
        entityMapper.updateEntityFromResult(existing, entityMapper.toResult(newData));
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
}