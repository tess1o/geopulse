package org.github.tess1o.geopulse.geocoding.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.locationtech.jts.geom.Point;

import java.util.Optional;

/**
 * Simplified geocoding service implementation.
 * Uses the simplified schema that stores only formatted display names.
 * Uses GeocodingProviderFactory for multi-provider support with failover.
 */
@Slf4j
@ApplicationScoped
public class GeocodingServiceImpl implements GeocodingService {

    private final GeocodingProviderFactory providerFactory;
    private final CacheGeocodingService cacheService;

    @Inject
    public GeocodingServiceImpl(GeocodingProviderFactory providerFactory,
                                CacheGeocodingService cacheService) {
        this.providerFactory = providerFactory;
        this.cacheService = cacheService;
    }

    @Override
    public FormattableGeocodingResult getLocationName(Point point) {
        if (point == null) {
            throw new IllegalArgumentException("Point cannot be null");
        }

        double longitude = point.getX();
        double latitude = point.getY();

        Optional<FormattableGeocodingResult> cachedResult = cacheService.getCachedGeocodingResult(point);
        if (cachedResult.isPresent()) {
            log.debug("Using cached result for coordinates: lon={}, lat={}", longitude, latitude);
            return cachedResult.get();
        }

        // If not in cache, fetch from external provider
        log.info("No cached location found, fetching from external service: lon={}, lat={}", longitude, latitude);
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
            return org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult.builder()
                    .requestCoordinates(point)
                    .resultCoordinates(point)
                    .formattedDisplayName(String.format("Address not found (%.6f, %.6f)", latitude, longitude))
                    .providerName("fallback")
                    .build();
        }
    }
}