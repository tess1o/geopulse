package org.github.tess1o.geopulse.geocoding.service.external;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geocoding.client.GoogleMapsRestClient;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfig;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.github.tess1o.geopulse.geocoding.adapter.GoogleMapsResponseAdapter;
import org.github.tess1o.geopulse.geocoding.exception.GeocodingException;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.locationtech.jts.geom.Point;

import java.time.temporal.ChronoUnit;

/**
 * Google Maps geocoding service that handles API calls and response transformation.
 * Returns structured FormattableGeocodingResult instead of raw responses.
 */
@ApplicationScoped
@Slf4j
public class GoogleMapsGeocodingService {

    private final GoogleMapsRestClient googleMapsClient;
    private final GoogleMapsResponseAdapter adapter;
    private final GeocodingConfig config;

    @Inject
    public GoogleMapsGeocodingService(@RestClient GoogleMapsRestClient googleMapsClient,
                                    GoogleMapsResponseAdapter adapter,
                                    GeocodingConfig config) {
        this.googleMapsClient = googleMapsClient;
        this.adapter = adapter;
        this.config = config;
    }

    /**
     * Reverse geocode coordinates using Google Maps API with retry, rate limiting, and circuit breaker.
     * - Higher rate limit than Nominatim as Google Maps has more generous limits
     * - Circuit breaker with more conservative settings for paid service
     * - Retry handles transient failures
     * 
     * @param requestCoordinates The coordinates to reverse geocode
     * @return Structured geocoding result
     */
    @Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS, jitter = 100)
    @Bulkhead(value = 5, waitingTaskQueue = 20) // Max 5 concurrent requests, larger queue
    @CircuitBreaker(failureRatio = 0.6, requestVolumeThreshold = 10, delay = 60, delayUnit = ChronoUnit.SECONDS)
    public Uni<FormattableGeocodingResult> reverseGeocode(Point requestCoordinates) {
        if (!isEnabled()) {
            return Uni.createFrom().failure(new GeocodingException("Google Maps provider is disabled"));
        }

        String apiKey = config.googlemaps().apiKey();
        if (apiKey.isEmpty()) {
            return Uni.createFrom().failure(new GeocodingException("Google Maps API key not configured"));
        }

        double longitude = requestCoordinates.getX();
        double latitude = requestCoordinates.getY();

        log.debug("Calling Google Maps for coordinates: lon={}, lat={}", longitude, latitude);

        String latlng = String.format("%.6f,%.6f", latitude, longitude);
        return googleMapsClient.reverseGeocode(latlng, apiKey, "street_address|establishment")
                .map(response -> {
                    String summary = response.getResults().isEmpty() ? "No results" :
                            response.getResults().getFirst().getFormattedAddress();
                    log.debug("Google Maps response received: status={}, firstResult={}", response.getStatus(), summary);
                    return adapter.adapt(response, requestCoordinates, getProviderName());
                })
                .onItem().ifNull().failWith(() -> {
                    log.error("Google Maps adapter returned null for coordinates: lon={}, lat={}", longitude, latitude);
                    return new GeocodingException("Google Maps adapter returned null result");
                })
                .onFailure().transform(failure -> {
                    log.error("Google Maps API call failed for coordinates: lon={}, lat={}", longitude, latitude, failure);
                    return new GeocodingException("Google Maps geocoding failed", failure);
                });
    }

    /**
     * Check if this provider is enabled and properly configured.
     * 
     * @return true if enabled and has API key
     */
    public boolean isEnabled() {
        return config.provider().googlemaps().enabled() && !config.googlemaps().apiKey().isEmpty();
    }

    /**
     * Get the provider name.
     * 
     * @return Provider name
     */
    public String getProviderName() {
        return "GoogleMaps";
    }
}