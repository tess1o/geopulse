package org.github.tess1o.geopulse.geocoding.service.external;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.github.tess1o.geopulse.geocoding.adapter.PhotonResponseAdapter;
import org.github.tess1o.geopulse.geocoding.client.PhotonRestClient;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfigurationService;
import org.github.tess1o.geopulse.geocoding.exception.GeocodingException;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.locationtech.jts.geom.Point;

import java.net.URI;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * Nominatim geocoding service that handles API calls and response transformation.
 * Returns structured FormattableGeocodingResult instead of raw responses.
 */
@ApplicationScoped
@Slf4j
public class PhotonGeocodingService {

    private final PhotonResponseAdapter adapter;
    private final GeocodingConfigurationService configService;
    private final String defaultUrl;

    @Inject
    public PhotonGeocodingService(PhotonResponseAdapter adapter,
                                  GeocodingConfigurationService configService,
                                  @ConfigProperty(name = "quarkus.rest-client.photon-api.url") String defaultUrl) {
        this.adapter = adapter;
        this.configService = configService;
        this.defaultUrl = defaultUrl;
    }

    /**
     * Get the Photon REST client with the current configured URL.
     * Builds client dynamically to support runtime URL changes.
     */
    private PhotonRestClient getClient() {
        String url = configService.getPhotonUrl().orElse(defaultUrl);
        try {
            return RestClientBuilder.newBuilder()
                    .baseUri(URI.create(url))
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .property("microprofile.rest.client.disable.default.mapper", true)
                    .build(PhotonRestClient.class);
        } catch (Exception e) {
            log.error("Failed to build Photon REST client with URL: {}", url, e);
            throw new RuntimeException("Failed to build Photon REST client", e);
        }
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

        // Get language from global configuration (if set)
        String language = configService.getPhotonLanguage().orElse(null);

        if (language != null) {
            log.debug("Calling Photon for coordinates: lon={}, lat={}, language={}",
                      longitude, latitude, language);
        } else {
            log.debug("Calling Photon for coordinates: lon={}, lat={} (no language header)",
                      longitude, latitude);
        }

        PhotonRestClient client = getClient();
        return client.getAddress(longitude, latitude, language)
                .map(response -> {
                    log.debug("Photon response received: {}", response);
                    return adapter.adapt(response, requestCoordinates, getProviderName());
                })
                .onItem().ifNull().failWith(() -> {
                    log.error("Photon adapter returned null for coordinates: lon={}, lat={}", longitude, latitude);
                    return new GeocodingException("Photon adapter returned null result");
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
        return configService.isPhotonEnabled();
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