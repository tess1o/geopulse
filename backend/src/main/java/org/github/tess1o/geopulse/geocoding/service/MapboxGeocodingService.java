package org.github.tess1o.geopulse.geocoding.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geocoding.client.MapboxRestClient;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfig;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.github.tess1o.geopulse.geocoding.adapter.MapboxResponseAdapter;
import org.github.tess1o.geopulse.geocoding.exception.GeocodingException;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.locationtech.jts.geom.Point;

import java.time.temporal.ChronoUnit;

/**
 * Mapbox geocoding service that handles API calls and response transformation.
 * Returns structured FormattableGeocodingResult instead of raw responses.
 */
@ApplicationScoped
@Slf4j
public class MapboxGeocodingService {

    private final MapboxRestClient mapboxClient;
    private final MapboxResponseAdapter adapter;
    private final GeocodingConfig config;

    @Inject
    public MapboxGeocodingService(@RestClient MapboxRestClient mapboxClient,
                                MapboxResponseAdapter adapter,
                                GeocodingConfig config) {
        this.mapboxClient = mapboxClient;
        this.adapter = adapter;
        this.config = config;
    }

    /**
     * Reverse geocode coordinates using Mapbox API with retry, rate limiting, and circuit breaker.
     * - Moderate rate limit as Mapbox has reasonable API limits
     * - Circuit breaker with balanced settings
     * - Retry handles transient failures
     * 
     * @param requestCoordinates The coordinates to reverse geocode
     * @return Structured geocoding result
     */
    @Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS, jitter = 100)
    @Bulkhead(value = 3, waitingTaskQueue = 15) // Max 3 concurrent requests, moderate queue
    @CircuitBreaker(failureRatio = 0.55, requestVolumeThreshold = 6, delay = 45, delayUnit = ChronoUnit.SECONDS)
    public Uni<FormattableGeocodingResult> reverseGeocode(Point requestCoordinates) {
        if (!isEnabled()) {
            return Uni.createFrom().failure(new GeocodingException("Mapbox provider is disabled"));
        }

        String accessToken = config.mapbox().accessToken();
        if (accessToken.isEmpty()) {
            return Uni.createFrom().failure(new GeocodingException("Mapbox access token not configured"));
        }

        double longitude = requestCoordinates.getX();
        double latitude = requestCoordinates.getY();

        log.debug("Calling Mapbox for coordinates: lon={}, lat={}", longitude, latitude);

        return mapboxClient.reverseGeocode(longitude, latitude, accessToken, "poi,address")
                .map(response -> {
                    String summary = response.getFeatures().isEmpty() ? "No features" :
                            response.getFeatures().get(0).getPlaceName();
                    log.debug("Mapbox response received: type={}, firstFeature={}", response.getType(), summary);
                    return adapter.adapt(response, requestCoordinates, getProviderName());
                })
                .onFailure().transform(failure -> {
                    log.error("Mapbox API call failed for coordinates: lon={}, lat={}", longitude, latitude, failure);
                    return new GeocodingException("Mapbox geocoding failed", failure);
                });
    }

    /**
     * Check if this provider is enabled and properly configured.
     * 
     * @return true if enabled and has access token
     */
    public boolean isEnabled() {
        return config.provider().mapbox().enabled() && !config.mapbox().accessToken().isEmpty();
    }

    /**
     * Get the provider name.
     * 
     * @return Provider name
     */
    public String getProviderName() {
        return "Mapbox";
    }
}