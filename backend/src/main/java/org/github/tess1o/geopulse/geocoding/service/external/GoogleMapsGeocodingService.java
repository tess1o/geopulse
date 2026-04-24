package org.github.tess1o.geopulse.geocoding.service.external;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geocoding.client.GoogleMapsRestClient;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfigurationService;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.github.tess1o.geopulse.geocoding.adapter.GoogleMapsResponseAdapter;
import org.github.tess1o.geopulse.geocoding.exception.GeocodingException;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.common.GeocodingSearchResult;
import org.github.tess1o.geopulse.geocoding.model.googlemaps.GoogleMapsResponse;
import org.github.tess1o.geopulse.geocoding.model.googlemaps.GoogleMapsResult;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.locationtech.jts.geom.Point;

import java.util.ArrayList;
import java.util.List;

/**
 * Google Maps geocoding service that handles API calls and response transformation.
 * Returns structured FormattableGeocodingResult instead of raw responses.
 */
@ApplicationScoped
@Slf4j
public class GoogleMapsGeocodingService {

    private final GoogleMapsRestClient googleMapsClient;
    private final GoogleMapsResponseAdapter adapter;
    private final GeocodingConfigurationService configService;

    @Inject
    public GoogleMapsGeocodingService(@RestClient GoogleMapsRestClient googleMapsClient,
                                    GoogleMapsResponseAdapter adapter,
                                    GeocodingConfigurationService configService) {
        this.googleMapsClient = googleMapsClient;
        this.adapter = adapter;
        this.configService = configService;
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
    // Retry/circuit breaker thresholds are tuned globally via quarkus.fault-tolerance.global.* properties.
    @Retry
    @Bulkhead(value = 5, waitingTaskQueue = 20) // Max 5 concurrent requests, larger queue
    @CircuitBreaker
    public Uni<FormattableGeocodingResult> reverseGeocode(Point requestCoordinates) {
        if (!isEnabled()) {
            return Uni.createFrom().failure(new GeocodingException("Google Maps provider is disabled"));
        }

        String apiKey = configService.getGoogleMapsApiKey();
        if (apiKey.isEmpty()) {
            return Uni.createFrom().failure(new GeocodingException("Google Maps API key not configured"));
        }

        double longitude = requestCoordinates.getX();
        double latitude = requestCoordinates.getY();

        String language = configService.getGoogleMapsLanguage().orElse(null);
        if (language != null) {
            log.debug("Calling Google Maps for coordinates: lon={}, lat={}, language={}",
                    longitude, latitude, language);
        } else {
            log.debug("Calling Google Maps for coordinates: lon={}, lat={} (default language behavior)",
                    longitude, latitude);
        }

        String latlng = String.format("%.6f,%.6f", latitude, longitude);
        return googleMapsClient.reverseGeocode(latlng, apiKey, "street_address|establishment", language)
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
        String apiKey = configService.getGoogleMapsApiKey();
        return configService.isGoogleMapsEnabled() && apiKey != null && !apiKey.isBlank();
    }

    /**
     * Get the provider name.
     * 
     * @return Provider name
     */
    public String getProviderName() {
        return "GoogleMaps";
    }

    @Retry
    @Bulkhead(value = 5, waitingTaskQueue = 20)
    @CircuitBreaker
    public Uni<List<GeocodingSearchResult>> forwardSearch(String query, Point biasCenter, int limit) {
        if (!isEnabled()) {
            return Uni.createFrom().failure(new GeocodingException("Google Maps provider is disabled"));
        }

        String apiKey = configService.getGoogleMapsApiKey();
        if (apiKey.isEmpty()) {
            return Uni.createFrom().failure(new GeocodingException("Google Maps API key not configured"));
        }

        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.length() < 2) {
            return Uni.createFrom().item(List.of());
        }

        String language = configService.getGoogleMapsLanguage().orElse(null);
        String bounds = buildBiasBounds(biasCenter);
        int safeLimit = Math.max(1, Math.min(limit, 20));

        return googleMapsClient.forwardGeocode(safeQuery, apiKey, language, bounds)
                .map(response -> mapSearchResponse(response, biasCenter, safeLimit))
                .onFailure().transform(failure -> {
                    log.error("Google Maps forward search failed for query='{}'", safeQuery, failure);
                    return new GeocodingException("Google Maps forward search failed", failure);
                });
    }

    private List<GeocodingSearchResult> mapSearchResponse(GoogleMapsResponse response, Point fallbackPoint, int limit) {
        List<GeocodingSearchResult> mapped = new ArrayList<>();
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return mapped;
        }

        for (GoogleMapsResult result : response.getResults()) {
            if (mapped.size() >= limit) {
                break;
            }

            Point requestPoint = createPoint(result, fallbackPoint);
            if (requestPoint == null) {
                continue;
            }

            GoogleMapsResponse single = new GoogleMapsResponse();
            single.setStatus(response.getStatus());
            single.setErrorMessage(response.getErrorMessage());
            single.setResults(List.of(result));

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

    private Point createPoint(GoogleMapsResult result, Point fallbackPoint) {
        if (result != null
                && result.getGeometry() != null
                && result.getGeometry().getLocation() != null) {
            try {
                return GeoUtils.createPoint(
                        result.getGeometry().getLocation().getLng(),
                        result.getGeometry().getLocation().getLat()
                );
            } catch (Exception ignored) {
                // Fall through to fallback point.
            }
        }
        return fallbackPoint;
    }

    private String buildBiasBounds(Point biasCenter) {
        if (biasCenter == null) {
            return null;
        }

        double lat = biasCenter.getY();
        double lon = biasCenter.getX();
        double latDelta = 0.35;
        double lonDelta = 0.35;

        double south = Math.max(-90.0, lat - latDelta);
        double west = Math.max(-180.0, lon - lonDelta);
        double north = Math.min(90.0, lat + latDelta);
        double east = Math.min(180.0, lon + lonDelta);

        return String.format("%.6f,%.6f|%.6f,%.6f", south, west, north, east);
    }
}
