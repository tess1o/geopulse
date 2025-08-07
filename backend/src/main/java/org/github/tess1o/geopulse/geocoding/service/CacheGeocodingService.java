package org.github.tess1o.geopulse.geocoding.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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
    double spatialToleranceMeters;

    @Inject
    public CacheGeocodingService(ReverseGeocodingLocationRepository repository) {
        this.repository = repository;
    }

    /**
     * Get cached result for the given coordinates.
     *
     * @param requestCoordinates The coordinates to look up
     * @return Formatted display name if found
     */
    public Optional<String> getCachedDisplayName(Point requestCoordinates) {
        if (requestCoordinates == null) {
            return Optional.empty();
        }

        try {
            ReverseGeocodingLocationEntity match = repository.findByRequestCoordinates(
                    requestCoordinates, spatialToleranceMeters
            );

            if (match != null) {
                log.debug("Cache hit for coordinates: lon={}, lat={} (tolerance: {}m), provider: {}",
                        requestCoordinates.getX(), requestCoordinates.getY(), spatialToleranceMeters, match.getProviderName());
                return Optional.of(match.getDisplayName());
            }

            log.debug("Cache miss for coordinates: lon={}, lat={}",
                    requestCoordinates.getX(), requestCoordinates.getY());
            return Optional.empty();

        } catch (Exception e) {
            log.error("Error retrieving cached result for coordinates: lon={}, lat={}",
                    requestCoordinates.getX(), requestCoordinates.getY(), e);
            throw new GeocodingCacheException("Failed to retrieve cached result", e);
        }
    }

    /**
     * Get cached result for the given coordinates as a FormattableGeocodingResult.
     *
     * @param requestCoordinates The coordinates to look up
     * @return Full geocoding result if found
     */
    public Optional<FormattableGeocodingResult> getCachedGeocodingResult(Point requestCoordinates) {
        if (requestCoordinates == null) {
            return Optional.empty();
        }

        try {
            ReverseGeocodingLocationEntity match = repository.findByRequestCoordinates(
                    requestCoordinates, spatialToleranceMeters
            );

            if (match != null) {
                log.debug("Cache hit for coordinates: lon={}, lat={} (tolerance: {}m), provider: {}",
                        requestCoordinates.getX(), requestCoordinates.getY(), spatialToleranceMeters, match.getProviderName());
                
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

            log.debug("Cache miss for coordinates: lon={}, lat={}",
                    requestCoordinates.getX(), requestCoordinates.getY());
            return Optional.empty();

        } catch (Exception e) {
            log.error("Error retrieving cached result for coordinates: lon={}, lat={}",
                    requestCoordinates.getX(), requestCoordinates.getY(), e);
            throw new GeocodingCacheException("Failed to retrieve cached result", e);
        }
    }

    /**
     * Get the entity ID for a cached geocoding result.
     *
     * @param requestCoordinates The coordinates to look up
     * @return Entity ID if found in cache
     */
    public Optional<Long> getCachedGeocodingResultId(Point requestCoordinates) {
        if (requestCoordinates == null) {
            return Optional.empty();
        }

        try {
            ReverseGeocodingLocationEntity match = repository.findByRequestCoordinates(
                    requestCoordinates, spatialToleranceMeters
            );

            if (match != null) {
                log.debug("Found cached entity ID {} for coordinates: lon={}, lat={}",
                        match.getId(), requestCoordinates.getX(), requestCoordinates.getY());
                return Optional.of(match.getId());
            }

            return Optional.empty();

        } catch (Exception e) {
            log.error("Error retrieving cached entity ID for coordinates: lon={}, lat={}",
                    requestCoordinates.getX(), requestCoordinates.getY(), e);
            return Optional.empty();
        }
    }

    /**
     * Cache a structured geocoding result.
     *
     * @param geocodingResult The structured geocoding result to cache
     */
    @Transactional
    public void cacheGeocodingResult(FormattableGeocodingResult geocodingResult) {
        if (geocodingResult == null || geocodingResult.getRequestCoordinates() == null) {
            throw new IllegalArgumentException("Geocoding result and request coordinates cannot be null");
        }

        Point requestCoordinates = geocodingResult.getRequestCoordinates();

        try {
            // Convert to entity and save
            ReverseGeocodingLocationEntity entity = convertToEntity(geocodingResult);

            // Check if we already have this exact location
            ReverseGeocodingLocationEntity existing = repository.findByExactCoordinates(requestCoordinates);
            if (existing != null) {
                // Update existing entry
                existing.setResultCoordinates(entity.getResultCoordinates());
                existing.setBoundingBox(entity.getBoundingBox());
                existing.setDisplayName(entity.getDisplayName());
                existing.setProviderName(entity.getProviderName());
                existing.setCity(entity.getCity());
                existing.setCountry(entity.getCountry());
                existing.setLastAccessedAt(Instant.now());

                repository.persist(existing);
                log.debug("Updated cached result for coordinates: lon={}, lat={}",
                        requestCoordinates.getX(), requestCoordinates.getY());
            } else {
                // Create new entry
                repository.persist(entity);
                log.debug("Cached new result for coordinates: lon={}, lat={}",
                        requestCoordinates.getX(), requestCoordinates.getY());
            }

        } catch (Exception e) {
            log.error("Error caching geocoding result for coordinates: lon={}, lat={}",
                    requestCoordinates.getX(), requestCoordinates.getY(), e);
            throw new GeocodingCacheException("Failed to cache geocoding result", e);
        }
    }

    /**
     * Batch get cached geocoding result IDs for multiple coordinates.
     * Optimized for timeline assembly to reduce database round-trips.
     *
     * @param coordinates List of coordinates to look up
     * @return Map of coordinate string (lon,lat) to entity ID
     */
    public Map<String, Long> getCachedGeocodingResultIdsBatch(List<Point> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            return Map.of();
        }

        try {
            Map<String, ReverseGeocodingLocationEntity> cachedResults = 
                repository.findByCoordinatesBatchReal(coordinates, spatialToleranceMeters);
            
            return cachedResults.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().getId()
                    ));

        } catch (Exception e) {
            log.error("Error retrieving batch cached entity IDs for {} coordinates", coordinates.size(), e);
            return Map.of();
        }
    }

    /**
     * Batch get cached geocoding results for multiple coordinates.
     * Optimized for timeline assembly to reduce database round-trips.
     *
     * @param coordinates List of coordinates to look up
     * @return Map of coordinate string (lon,lat) to FormattableGeocodingResult
     */
    public Map<String, FormattableGeocodingResult> getCachedGeocodingResultsBatch(List<Point> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            return Map.of();
        }

        try {
            Map<String, ReverseGeocodingLocationEntity> cachedResults = 
                repository.findByCoordinatesBatchReal(coordinates, spatialToleranceMeters);
            
            return cachedResults.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> convertFromEntity(entry.getValue())
                    ));

        } catch (Exception e) {
            log.error("Error retrieving batch cached results for {} coordinates", coordinates.size(), e);
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