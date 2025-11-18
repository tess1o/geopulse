package org.github.tess1o.geopulse.geocoding.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.locationtech.jts.geom.Point;

/**
 * Simplified geocoding service implementation.
 * Uses the simplified schema that stores only formatted display names.
 * Uses GeocodingProviderFactory for multi-provider support with failover.
 */
@Slf4j
@ApplicationScoped
public class GeocodingService {

    private final GeocodingProviderFactory providerFactory;
    private final CacheGeocodingService cacheService;

    @Inject
    public GeocodingService(GeocodingProviderFactory providerFactory,
                            CacheGeocodingService cacheService) {
        this.providerFactory = providerFactory;
        this.cacheService = cacheService;
    }

    public FormattableGeocodingResult getLocationName(Point point) {
        if (point == null) {
            throw new IllegalArgumentException("Point cannot be null");
        }

        double longitude = point.getX();
        double latitude = point.getY();

        // NOTE: Cache lookup is handled by LocationPointResolver with userId context.
        // This service just fetches from external provider and caches as original.
        log.info("Fetching from external geocoding service: lon={}, lat={}", longitude, latitude);
        return fetchAndCacheLocationName(point);
    }

    private FormattableGeocodingResult fetchAndCacheLocationName(Point point) {
        double longitude = point.getX();
        double latitude = point.getY();

        try {
            FormattableGeocodingResult geocodingResult = providerFactory.reverseGeocode(point)
                    .await().indefinitely();

            // Defensive null check - should never happen with proper adapter/service implementation
            if (geocodingResult == null) {
                log.error("Geocoding provider returned null result for coordinates: lon={}, lat={}", longitude, latitude);
                throw new IllegalStateException("Geocoding provider returned null result");
            }

            log.info("Successfully fetched address from {}: lon={}, lat={}, displayName={}",
                    geocodingResult.getProviderName(), longitude, latitude, geocodingResult.getFormattedDisplayName());

            // Cache the structured result
            try {
                cacheService.cacheGeocodingResult(geocodingResult);
                log.debug("Successfully cached result for coordinates: lon={}, lat={}", longitude, latitude);
            } catch (Exception cacheError) {
                log.warn("Failed to cache result for coordinates: lon={}, lat={}", longitude, latitude, cacheError);
            }

            return geocodingResult;

        } catch (Exception e) {
            log.error("Error getting address from coordinates: lon={}, lat={}", longitude, latitude, e);
            // Return a fallback FormattableGeocodingResult
            return SimpleFormattableResult.builder()
                    .requestCoordinates(point)
                    .resultCoordinates(point)
                    .formattedDisplayName(String.format("Address not found (%.6f, %.6f)", latitude, longitude))
                    .providerName("fallback")
                    .build();
        }
    }
}