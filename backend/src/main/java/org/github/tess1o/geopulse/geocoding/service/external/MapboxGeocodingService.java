package org.github.tess1o.geopulse.geocoding.service.external;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geocoding.client.MapboxRestClient;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfigurationService;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.github.tess1o.geopulse.geocoding.adapter.MapboxResponseAdapter;
import org.github.tess1o.geopulse.geocoding.exception.GeocodingException;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.common.GeocodingSearchResult;
import org.github.tess1o.geopulse.geocoding.model.mapbox.MapboxFeature;
import org.github.tess1o.geopulse.geocoding.model.mapbox.MapboxResponse;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.locationtech.jts.geom.Point;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapbox geocoding service that handles API calls and response transformation.
 * Returns structured FormattableGeocodingResult instead of raw responses.
 */
@ApplicationScoped
@Slf4j
public class MapboxGeocodingService {

    private final MapboxRestClient mapboxClient;
    private final MapboxResponseAdapter adapter;
    private final GeocodingConfigurationService configService;

    @Inject
    public MapboxGeocodingService(@RestClient MapboxRestClient mapboxClient,
                                MapboxResponseAdapter adapter,
                                GeocodingConfigurationService configService) {
        this.mapboxClient = mapboxClient;
        this.adapter = adapter;
        this.configService = configService;
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
    // Retry/circuit breaker thresholds are tuned globally via quarkus.fault-tolerance.global.* properties.
    @Retry
    @Bulkhead(value = 3, waitingTaskQueue = 15) // Max 3 concurrent requests, moderate queue
    @CircuitBreaker
    public Uni<FormattableGeocodingResult> reverseGeocode(Point requestCoordinates) {
        if (!isEnabled()) {
            return Uni.createFrom().failure(new GeocodingException("Mapbox provider is disabled"));
        }

        String accessToken = configService.getMapboxAccessToken();
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
                .onItem().ifNull().failWith(() -> {
                    log.error("Mapbox adapter returned null for coordinates: lon={}, lat={}", longitude, latitude);
                    return new GeocodingException("Mapbox adapter returned null result");
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
        String accessToken = configService.getMapboxAccessToken();
        return configService.isMapboxEnabled() && accessToken != null && !accessToken.isBlank();
    }

    /**
     * Get the provider name.
     * 
     * @return Provider name
     */
    public String getProviderName() {
        return "Mapbox";
    }

    @Retry
    @Bulkhead(value = 3, waitingTaskQueue = 15)
    @CircuitBreaker
    public Uni<List<GeocodingSearchResult>> forwardSearch(String query, Point biasCenter, int limit) {
        if (!isEnabled()) {
            return Uni.createFrom().failure(new GeocodingException("Mapbox provider is disabled"));
        }

        String accessToken = configService.getMapboxAccessToken();
        if (accessToken.isEmpty()) {
            return Uni.createFrom().failure(new GeocodingException("Mapbox access token not configured"));
        }

        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.length() < 2) {
            return Uni.createFrom().item(List.of());
        }

        int safeLimit = Math.max(1, Math.min(limit, 20));
        String proximity = biasCenter == null
                ? null
                : String.format("%.6f,%.6f", biasCenter.getX(), biasCenter.getY());

        return mapboxClient.forwardGeocode(safeQuery, accessToken, "poi,address,place,postcode", safeLimit, proximity)
                .map(response -> mapSearchResponse(response, biasCenter, safeLimit))
                .onFailure().transform(failure -> {
                    log.error("Mapbox forward search failed for query='{}'", safeQuery, failure);
                    return new GeocodingException("Mapbox forward search failed", failure);
                });
    }

    private List<GeocodingSearchResult> mapSearchResponse(MapboxResponse response, Point fallbackPoint, int limit) {
        List<GeocodingSearchResult> mapped = new ArrayList<>();
        if (response == null || response.getFeatures() == null || response.getFeatures().isEmpty()) {
            return mapped;
        }

        for (MapboxFeature feature : response.getFeatures()) {
            if (mapped.size() >= limit) {
                break;
            }

            Point requestPoint = createPoint(feature, fallbackPoint);
            if (requestPoint == null) {
                continue;
            }

            MapboxResponse single = new MapboxResponse();
            single.setType(response.getType());
            single.setAttribution(response.getAttribution());
            single.setFeatures(List.of(feature));
            single.setQuery(response.getQuery());

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

    private Point createPoint(MapboxFeature feature, Point fallbackPoint) {
        if (feature != null
                && feature.getGeometry() != null
                && feature.getGeometry().getCoordinates() != null
                && feature.getGeometry().getCoordinates().size() >= 2) {
            try {
                return GeoUtils.createPoint(
                        feature.getGeometry().getCoordinates().get(0),
                        feature.getGeometry().getCoordinates().get(1)
                );
            } catch (Exception ignored) {
                // Fall through to fallback point.
            }
        }
        return fallbackPoint;
    }
}
