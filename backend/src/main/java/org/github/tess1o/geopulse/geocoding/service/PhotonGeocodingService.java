package org.github.tess1o.geopulse.geocoding.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.github.tess1o.geopulse.geocoding.adapter.PhotonResponseAdapter;
import org.github.tess1o.geopulse.geocoding.client.PhotonRestClient;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfig;
import org.github.tess1o.geopulse.geocoding.exception.GeocodingException;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.locationtech.jts.geom.Point;

import java.time.temporal.ChronoUnit;

/**
 * Nominatim geocoding service that handles API calls and response transformation.
 * Returns structured FormattableGeocodingResult instead of raw responses.
 */
@ApplicationScoped
@Slf4j
public class PhotonGeocodingService {

    private final PhotonRestClient photonClient;
    private final PhotonResponseAdapter adapter;
    private final GeocodingConfig config;

    @Inject
    public PhotonGeocodingService(@RestClient PhotonRestClient photonClient,
                                  PhotonResponseAdapter adapter,
                                  GeocodingConfig config) {
        this.photonClient = photonClient;
        this.adapter = adapter;
        this.config = config;
    }

    /**
     * Reverse geocode coordinates using Nominatim API with retry, rate limiting, and circuit breaker.
     * - Rate limited to 1 request/second to respect Nominatim usage policy
     * - Circuit breaker protects against cascading failures
     * - Retry handles transient failures
     *
     * @param requestCoordinates The coordinates to reverse geocode
     * @return Structured geocoding result
     */
    @Retry(delay = 500, delayUnit = ChronoUnit.MILLIS, jitter = 100)
    @Bulkhead(value = 2, waitingTaskQueue = 10) // Max 1 concurrent request, queue up to 10
    @CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 4, delay = 30, delayUnit = ChronoUnit.SECONDS)
    public Uni<FormattableGeocodingResult> reverseGeocode(Point requestCoordinates) {
        if (!isEnabled()) {
            return Uni.createFrom().failure(new GeocodingException("Photon provider is disabled"));
        }

        double longitude = requestCoordinates.getX();
        double latitude = requestCoordinates.getY();

        log.debug("Calling Photon for coordinates: lon={}, lat={}", longitude, latitude);

        return photonClient.getAddress(longitude, latitude)
                .map(response -> {
                    log.debug("Photon response received: {}", response);
                    return adapter.adapt(response, requestCoordinates, getProviderName());
                })
                .onFailure().transform(failure -> {
                    log.error("Photon API call failed for coordinates: lon={}, lat={}", longitude, latitude, failure);
                    return new GeocodingException("Photon geocoding failed", failure);
                });
    }

    /**
     * Check if this provider is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return config.provider().photon().enabled();
    }

    /**
     * Get the provider name.
     *
     * @return Provider name
     */
    public String getProviderName() {
        return "Photon";
    }
}