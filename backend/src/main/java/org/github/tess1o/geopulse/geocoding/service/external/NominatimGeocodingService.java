package org.github.tess1o.geopulse.geocoding.service.external;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.github.tess1o.geopulse.geocoding.client.NominatimRestClient;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfigurationService;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.github.tess1o.geopulse.geocoding.adapter.NominatimResponseAdapter;
import org.github.tess1o.geopulse.geocoding.exception.GeocodingException;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.common.GeocodingSearchResult;
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
public class NominatimGeocodingService {

    private final NominatimResponseAdapter adapter;
    private final GeocodingConfigurationService configService;
    private final String defaultUrl;
    private final String userAgent;

    @Inject
    public NominatimGeocodingService(NominatimResponseAdapter adapter,
                                     GeocodingConfigurationService configService,
                                     @ConfigProperty(name = "quarkus.rest-client.nominatim-api.url") String defaultUrl,
                                     @ConfigProperty(name = "quarkus.rest-client.nominatim-api.user-agent", defaultValue = "GeoPulse/1.0") String userAgent) {
        this.adapter = adapter;
        this.configService = configService;
        this.defaultUrl = defaultUrl;
        this.userAgent = userAgent;
    }

    /**
     * Get the Nominatim REST client with the current configured URL.
     * Builds client dynamically to support runtime URL changes.
     */
    private NominatimRestClient getClient() {
        String url = configService.getNominatimUrl().orElse(defaultUrl);
        try {
            return RestClientBuilder.newBuilder()
                    .baseUri(URI.create(url))
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .property("microprofile.rest.client.disable.default.mapper", true)
                    .property("User-Agent", userAgent)
                    .build(NominatimRestClient.class);
        } catch (Exception e) {
            log.error("Failed to build Nominatim REST client with URL: {}", url, e);
            throw new RuntimeException("Failed to build Nominatim REST client", e);
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
    @Bulkhead(value = 1, waitingTaskQueue = 10) // Max 1 concurrent request, queue up to 10
    @CircuitBreaker
    public Uni<FormattableGeocodingResult> reverseGeocode(Point requestCoordinates) {
        if (!isEnabled()) {
            return Uni.createFrom().failure(new GeocodingException("Nominatim provider is disabled"));
        }

        double longitude = requestCoordinates.getX();
        double latitude = requestCoordinates.getY();

        // Get language from global configuration (if set)
        String language = configService.getNominatimLanguage().orElse(null);

        if (language != null) {
            log.debug("Calling Nominatim for coordinates: lon={}, lat={}, language={}",
                      longitude, latitude, language);
        } else {
            log.debug("Calling Nominatim for coordinates: lon={}, lat={} (no language header)",
                      longitude, latitude);
        }

        NominatimRestClient client = getClient();
        return client.getAddress("json", longitude, latitude, language)
                .map(response -> {
                    log.debug("Nominatim response received: {}", response.getDisplayName());
                    return adapter.adapt(response, requestCoordinates, getProviderName());
                })
                .onItem().ifNull().failWith(() -> {
                    log.error("Nominatim adapter returned null for coordinates: lon={}, lat={}", longitude, latitude);
                    return new GeocodingException("Nominatim adapter returned null result");
                })
                .onFailure().transform(failure -> {
                    log.error("Nominatim API call failed for coordinates: lon={}, lat={}", longitude, latitude, failure);
                    return new GeocodingException("Nominatim geocoding failed", failure);
                });
    }

    /**
     * Check if this provider is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return configService.isNominatimEnabled();
    }

    /**
     * Get the provider name.
     *
     * @return Provider name
     */
    public String getProviderName() {
        return "Nominatim";
    }

    @Retry
    @Bulkhead(value = 1, waitingTaskQueue = 10)
    @CircuitBreaker
    public Uni<List<GeocodingSearchResult>> forwardSearch(String query, Point biasCenter, int limit) {
        if (!isEnabled()) {
            return Uni.createFrom().failure(new GeocodingException("Nominatim provider is disabled"));
        }

        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.length() < 2) {
            return Uni.createFrom().item(List.of());
        }

        boolean allowPublicHostForwardSearch = configService.isNominatimPublicHostForwardSearchEnabled();
        if (!allowPublicHostForwardSearch && isPublicNominatimHost()) {
            log.warn("Nominatim forward search rejected for query='{}': public host is blocked while geocoding.nominatim.public-host-forward-search-enabled=false",
                    safeQuery);
            return Uni.createFrom().failure(new GeocodingException(
                    "Nominatim forward search is disabled for public host by configuration"));
        }

        int safeLimit = Math.max(1, Math.min(limit, 20));
        String language = configService.getNominatimLanguage().orElse(null);
        String viewbox = buildSearchViewbox(biasCenter);

        return getClient().search("jsonv2", safeQuery, safeLimit, 1, viewbox, 0, language)
                .map(results -> {
                    List<GeocodingSearchResult> mapped = new ArrayList<>();
                    if (results == null || results.isEmpty()) {
                        return mapped;
                    }

                    for (var response : results) {
                        Point requestPoint = createRequestPoint(response, biasCenter);
                        if (requestPoint == null) {
                            continue;
                        }

                        FormattableGeocodingResult adapted = adapter.adapt(response, requestPoint, getProviderName());
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
                })
                .onFailure().transform(failure -> {
                    log.error("Nominatim forward search failed for query='{}'", safeQuery, failure);
                    return new GeocodingException("Nominatim forward search failed", failure);
                });
    }

    private boolean isPublicNominatimHost() {
        String url = configService.getNominatimUrl().orElse(defaultUrl);
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null && host.equalsIgnoreCase("nominatim.openstreetmap.org");
        } catch (Exception e) {
            log.warn("Failed to parse Nominatim URL '{}' for host validation", url, e);
            return false;
        }
    }

    private String buildSearchViewbox(Point biasCenter) {
        if (biasCenter == null) {
            return null;
        }

        double lon = biasCenter.getX();
        double lat = biasCenter.getY();
        // Approximate ~40km search preference box around current map focus.
        double lonDelta = 0.35;
        double latDelta = 0.35;
        double minLon = Math.max(-180.0, lon - lonDelta);
        double minLat = Math.max(-90.0, lat - latDelta);
        double maxLon = Math.min(180.0, lon + lonDelta);
        double maxLat = Math.min(90.0, lat + latDelta);
        return String.format("%.6f,%.6f,%.6f,%.6f", minLon, minLat, maxLon, maxLat);
    }

    private Point createRequestPoint(org.github.tess1o.geopulse.geocoding.model.nominatim.NominatimResponse response,
                                     Point fallback) {
        if (response != null && response.getLon() != null && response.getLat() != null) {
            try {
                double lon = Double.parseDouble(response.getLon());
                double lat = Double.parseDouble(response.getLat());
                return org.github.tess1o.geopulse.shared.geo.GeoUtils.createPoint(lon, lat);
            } catch (Exception ignored) {
                // Fall through to fallback point.
            }
        }
        return fallback;
    }
}
