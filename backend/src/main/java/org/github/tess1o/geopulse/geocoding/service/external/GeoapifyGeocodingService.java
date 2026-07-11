package org.github.tess1o.geopulse.geocoding.service.external;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.github.tess1o.geopulse.geocoding.adapter.GeoapifyResponseAdapter;
import org.github.tess1o.geopulse.geocoding.client.GeoapifyRestClient;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfigurationService;
import org.github.tess1o.geopulse.geocoding.exception.GeocodingException;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.common.GeocodingSearchResult;
import org.github.tess1o.geopulse.geocoding.model.geoapify.GeoapifyResponse;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.locationtech.jts.geom.Point;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@Slf4j
public class GeoapifyGeocodingService {

    private final GeoapifyRestClient geoapifyClient;
    private final GeoapifyResponseAdapter adapter;
    private final GeocodingConfigurationService configService;

    @Inject
    public GeoapifyGeocodingService(@RestClient GeoapifyRestClient geoapifyClient,
                                    GeoapifyResponseAdapter adapter,
                                    GeocodingConfigurationService configService) {
        this.geoapifyClient = geoapifyClient;
        this.adapter = adapter;
        this.configService = configService;
    }

    @Retry
    @Bulkhead(value = 5, waitingTaskQueue = 20)
    @CircuitBreaker
    public Uni<FormattableGeocodingResult> reverseGeocode(Point requestCoordinates) {
        if (!isEnabled()) {
            return Uni.createFrom().failure(new GeocodingException("Geoapify provider is disabled"));
        }

        String apiKey = configService.getGeoapifyApiKey();
        if (apiKey.isEmpty()) {
            return Uni.createFrom().failure(new GeocodingException("Geoapify API key not configured"));
        }

        double longitude = requestCoordinates.getX();
        double latitude = requestCoordinates.getY();
        String language = configService.getGeoapifyLanguage().orElse(null);

        return geoapifyClient.reverseGeocode(latitude, longitude, apiKey, language)
                .map(response -> adapter.adapt(response, requestCoordinates, getProviderName()))
                .onItem().ifNull().failWith(() -> new GeocodingException("Geoapify adapter returned null result"))
                .onFailure().transform(failure -> {
                    log.error("Geoapify API call failed for coordinates: lon={}, lat={}", longitude, latitude, failure);
                    return new GeocodingException("Geoapify geocoding failed", failure);
                });
    }

    public boolean isEnabled() {
        String apiKey = configService.getGeoapifyApiKey();
        return configService.isGeoapifyEnabled() && apiKey != null && !apiKey.isBlank();
    }

    public String getProviderName() {
        return "Geoapify";
    }

    @Retry
    @Bulkhead(value = 5, waitingTaskQueue = 20)
    @CircuitBreaker
    public Uni<List<GeocodingSearchResult>> forwardSearch(String query, Point biasCenter, int limit) {
        if (!isEnabled()) {
            return Uni.createFrom().failure(new GeocodingException("Geoapify provider is disabled"));
        }

        String apiKey = configService.getGeoapifyApiKey();
        if (apiKey.isEmpty()) {
            return Uni.createFrom().failure(new GeocodingException("Geoapify API key not configured"));
        }

        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.length() < 2) {
            return Uni.createFrom().item(List.of());
        }

        int safeLimit = Math.max(1, Math.min(limit, 20));
        String language = configService.getGeoapifyLanguage().orElse(null);
        String bias = biasCenter == null
                ? null
                : String.format("proximity:%.6f,%.6f", biasCenter.getX(), biasCenter.getY());

        return geoapifyClient.forwardGeocode(safeQuery, safeLimit, apiKey, language, bias)
                .map(response -> mapSearchResponse(response, biasCenter, safeLimit))
                .onFailure().transform(failure -> {
                    log.error("Geoapify forward search failed for query='{}'", safeQuery, failure);
                    return new GeocodingException("Geoapify forward search failed", failure);
                });
    }

    private List<GeocodingSearchResult> mapSearchResponse(GeoapifyResponse response, Point fallbackPoint, int limit) {
        List<GeocodingSearchResult> mapped = new ArrayList<>();
        List<GeoapifyResponse.Result> results = response == null ? List.of() : response.getEffectiveResults();
        if (results.isEmpty()) {
            return mapped;
        }

        for (GeoapifyResponse.Result result : results) {
            if (mapped.size() >= limit) {
                break;
            }

            Point requestPoint = createPoint(result, fallbackPoint);
            if (requestPoint == null) {
                continue;
            }

            GeoapifyResponse single = new GeoapifyResponse();
            single.setResults(List.of(result));

            FormattableGeocodingResult adapted = adapter.adapt(single, requestPoint, getProviderName());
            Point resultPoint = adapted.getResultCoordinates() != null
                    ? adapted.getResultCoordinates()
                    : requestPoint;

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

    private Point createPoint(GeoapifyResponse.Result result, Point fallbackPoint) {
        if (result != null && result.getLon() != null && result.getLat() != null) {
            try {
                return GeoUtils.createPoint(result.getLon(), result.getLat());
            } catch (Exception ignored) {
                // Fall through to fallback point.
            }
        }
        return fallbackPoint;
    }
}
