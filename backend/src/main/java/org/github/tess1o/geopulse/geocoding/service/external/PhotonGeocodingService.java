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
import org.github.tess1o.geopulse.geocoding.model.common.GeocodingSearchResult;
import org.github.tess1o.geopulse.geocoding.model.photon.PhotonResponse;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.locationtech.jts.geom.Point;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
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
    // Retry/circuit breaker thresholds are tuned globally via quarkus.fault-tolerance.global.* properties.
    @Retry
    @Bulkhead(value = 2, waitingTaskQueue = 10) // Max 2 concurrent requests, queue up to 10
    @CircuitBreaker
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

    @Retry
    @Bulkhead(value = 2, waitingTaskQueue = 10)
    @CircuitBreaker
    public Uni<List<GeocodingSearchResult>> forwardSearch(String query, Point biasCenter, int limit) {
        if (!isEnabled()) {
            return Uni.createFrom().failure(new GeocodingException("Photon provider is disabled"));
        }

        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.length() < 2) {
            return Uni.createFrom().item(List.of());
        }

        int safeLimit = Math.max(1, Math.min(limit, 20));
        String language = configService.getPhotonLanguage().orElse(null);
        Double biasLat = biasCenter == null ? null : biasCenter.getY();
        Double biasLon = biasCenter == null ? null : biasCenter.getX();

        return getClient().search(
                        safeQuery,
                        safeLimit,
                        biasLat,
                        biasLon,
                        biasCenter == null ? null : 12,
                        language,
                        language
                )
                .map(response -> mapSearchResponse(response, biasCenter))
                .onFailure().transform(failure -> {
                    log.error("Photon forward search failed for query='{}'", safeQuery, failure);
                    return new GeocodingException("Photon forward search failed", failure);
                });
    }

    private List<GeocodingSearchResult> mapSearchResponse(PhotonResponse response, Point fallbackPoint) {
        List<GeocodingSearchResult> mapped = new ArrayList<>();
        if (response == null || response.getFeatures() == null || response.getFeatures().isEmpty()) {
            return mapped;
        }

        for (PhotonResponse.Feature feature : response.getFeatures()) {
            if (feature == null) {
                continue;
            }

            Point requestPoint = createPoint(feature, fallbackPoint);
            if (requestPoint == null) {
                continue;
            }

            PhotonResponse single = new PhotonResponse();
            single.setType(response.getType());
            single.setFeatures(List.of(feature));

            FormattableGeocodingResult adapted = adapter.adapt(single, requestPoint, getProviderName());
            Point resultPoint = adapted.getResultCoordinates() != null
                    ? adapted.getResultCoordinates()
                    : requestPoint;

            if (resultPoint == null) {
                continue;
            }

            mapped.add(GeocodingSearchResult.builder()
                    .title(adapted.getFormattedDisplayName())
                    .latitude(resultPoint.getY())
                    .longitude(resultPoint.getX())
                    .city(adapted.getCity())
                    .country(adapted.getCountry())
                    .providerName(getProviderName())
                    .build());
        }

        return mapped;
    }

    private Point createPoint(PhotonResponse.Feature feature, Point fallbackPoint) {
        PhotonResponse.Geometry geometry = feature.getGeometry();
        if (geometry != null && geometry.getLongitude() != null && geometry.getLatitude() != null) {
            try {
                return GeoUtils.createPoint(geometry.getLongitude(), geometry.getLatitude());
            } catch (Exception ignored) {
                // Fall through to fallback point.
            }
        }
        return fallbackPoint;
    }
}
