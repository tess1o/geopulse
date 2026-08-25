package org.github.tess1o.geopulse.geocoding.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.github.tess1o.geopulse.geocoding.adapter.NominatimResponseAdapter;
import org.github.tess1o.geopulse.geocoding.adapter.PhotonResponseAdapter;
import org.github.tess1o.geopulse.geocoding.client.CustomHeadersRequestFilter;
import org.github.tess1o.geopulse.geocoding.client.NominatimRestClient;
import org.github.tess1o.geopulse.geocoding.client.PhotonRestClient;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfigurationService;
import org.github.tess1o.geopulse.geocoding.exception.GeocodingException;
import org.github.tess1o.geopulse.geocoding.model.CustomGeocodingProviderEntity;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.common.GeocodingSearchResult;
import org.github.tess1o.geopulse.geocoding.model.photon.PhotonResponse;
import org.github.tess1o.geopulse.geocoding.service.external.GoogleMapsGeocodingService;
import org.github.tess1o.geopulse.geocoding.service.external.ChibiGeoGeocodingService;
import org.github.tess1o.geopulse.geocoding.service.external.GeoapifyGeocodingService;
import org.github.tess1o.geopulse.geocoding.service.external.MapboxGeocodingService;
import org.github.tess1o.geopulse.geocoding.service.external.NominatimGeocodingService;
import org.github.tess1o.geopulse.geocoding.service.external.PhotonGeocodingService;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.locationtech.jts.geom.Point;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Factory service to handle multiple geocoding providers with failover.
 * Now uses dedicated provider services that return structured results.
 */
@ApplicationScoped
@Slf4j
public class GeocodingProviderFactory {

    private final NominatimGeocodingService nominatimService;
    private final GoogleMapsGeocodingService googleMapsService;
    private final MapboxGeocodingService mapboxService;
    private final PhotonGeocodingService photonService;
    private final GeoapifyGeocodingService geoapifyService;
    private final ChibiGeoGeocodingService chibiGeoService;
    private final GeocodingConfigurationService configService;
    private final CustomGeocodingProviderService customProviderService;
    private final PhotonResponseAdapter photonAdapter;
    private final NominatimResponseAdapter nominatimAdapter;
    private final String nominatimUserAgent;

    @Inject
    public GeocodingProviderFactory(NominatimGeocodingService nominatimService,
                                    GoogleMapsGeocodingService googleMapsService,
                                    MapboxGeocodingService mapboxService,
                                    PhotonGeocodingService photonService,
                                    GeoapifyGeocodingService geoapifyService,
                                    ChibiGeoGeocodingService chibiGeoService,
                                    GeocodingConfigurationService configService,
                                    CustomGeocodingProviderService customProviderService,
                                    PhotonResponseAdapter photonAdapter,
                                    NominatimResponseAdapter nominatimAdapter,
                                    @ConfigProperty(name = "quarkus.rest-client.nominatim-api.user-agent", defaultValue = "GeoPulse/1.0") String nominatimUserAgent) {
        this.nominatimService = nominatimService;
        this.googleMapsService = googleMapsService;
        this.mapboxService = mapboxService;
        this.photonService = photonService;
        this.geoapifyService = geoapifyService;
        this.chibiGeoService = chibiGeoService;
        this.configService = configService;
        this.customProviderService = customProviderService;
        this.photonAdapter = photonAdapter;
        this.nominatimAdapter = nominatimAdapter;
        this.nominatimUserAgent = nominatimUserAgent;
    }

    /**
     * Reverse geocode coordinates using primary provider with optional fallback.
     *
     * @param requestCoordinates The coordinates to reverse geocode
     * @return Structured geocoding result
     */
    public Uni<FormattableGeocodingResult> reverseGeocode(Point requestCoordinates) {
        String primaryProvider = configService.getPrimaryProvider();
        log.debug("Reverse geocoding coordinates: lon={}, lat={} using primary provider: {}",
                requestCoordinates.getX(), requestCoordinates.getY(), primaryProvider);

        // Try primary provider
        Uni<FormattableGeocodingResult> primaryResult = callProvider(primaryProvider, requestCoordinates);

        // If fallback is configured, try it on primary failure
        String fallbackProvider = configService.getFallbackProvider();
        if (!fallbackProvider.isEmpty() && !fallbackProvider.equalsIgnoreCase(primaryProvider)) {
            return primaryResult.onFailure().recoverWithUni(failure -> {
                log.warn("Primary provider '{}' failed, trying fallback provider '{}'",
                        primaryProvider, fallbackProvider, failure);
                return callProvider(fallbackProvider, requestCoordinates);
            });
        } else {
            log.debug("No valid fallback provider configured, returning primary result");
            return primaryResult;
        }
    }

    /**
     * Call a specific provider by name.
     */
    private Uni<FormattableGeocodingResult> callProvider(String providerName, Point requestCoordinates) {
        return switch (providerName.toLowerCase()) {
            case "nominatim" -> {
                if (!nominatimService.isEnabled()) {
                    yield Uni.createFrom().failure(new GeocodingException("Nominatim provider is disabled"));
                }
                yield nominatimService.reverseGeocode(requestCoordinates);
            }
            case "googlemaps" -> {
                if (!googleMapsService.isEnabled()) {
                    yield Uni.createFrom().failure(new GeocodingException("Google Maps provider is disabled or not configured"));
                }
                yield googleMapsService.reverseGeocode(requestCoordinates);
            }
            case "mapbox" -> {
                if (!mapboxService.isEnabled()) {
                    yield Uni.createFrom().failure(new GeocodingException("Mapbox provider is disabled or not configured"));
                }
                yield mapboxService.reverseGeocode(requestCoordinates);
            }
            case "photon" -> {
                if (!photonService.isEnabled()) {
                    yield Uni.createFrom().failure(new GeocodingException("Photon provider is disabled or not configured"));
                }
                yield photonService.reverseGeocode(requestCoordinates);
            }
            case "geoapify" -> {
                if (!geoapifyService.isEnabled()) {
                    yield Uni.createFrom().failure(new GeocodingException("Geoapify provider is disabled or not configured"));
                }
                yield geoapifyService.reverseGeocode(requestCoordinates);
            }
            case "chibigeo" -> {
                if (!chibiGeoService.isEnabled()) {
                    yield Uni.createFrom().failure(new GeocodingException("ChibiGeo provider is disabled or not configured"));
                }
                yield chibiGeoService.reverseGeocode(requestCoordinates);
            }
            default -> callCustomProvider(providerName, requestCoordinates);
        };
    }

    /**
     * Get available enabled providers for informational purposes.
     */
    public java.util.List<String> getEnabledProviders() {
        java.util.List<String> enabled = new java.util.ArrayList<>();
        if (nominatimService.isEnabled()) enabled.add("Nominatim");
        if (googleMapsService.isEnabled()) enabled.add("GoogleMaps");
        if (mapboxService.isEnabled()) enabled.add("Mapbox");
        if (photonService.isEnabled()) enabled.add("Photon");
        if (geoapifyService.isEnabled()) enabled.add("Geoapify");
        if (chibiGeoService.isEnabled()) enabled.add("ChibiGeo");
        customProviderService.listEnabledEntities().stream()
                .map(CustomGeocodingProviderEntity::getName)
                .forEach(enabled::add);
        return enabled;
    }

    /**
     * Reconcile coordinates with a specific provider (for manual reconciliation).
     * Does not use fallback - only uses the specified provider.
     *
     * @param providerName       The specific provider to use
     * @param requestCoordinates The coordinates to reconcile
     * @return Structured geocoding result
     */
    public Uni<FormattableGeocodingResult> reconcileWithProvider(String providerName, Point requestCoordinates) {
        log.debug("Reconciling coordinates with provider {}: lon={}, lat={}",
                providerName, requestCoordinates.getX(), requestCoordinates.getY());

        return callProvider(providerName, requestCoordinates);
    }

    /**
     * Forward search by query text using primary provider with optional fallback.
     */
    public Uni<List<GeocodingSearchResult>> forwardSearch(String query, Point biasCenter, int limit) {
        String primaryProvider = configService.getPrimaryProvider();
        log.info("Primary provider: {}", primaryProvider);
        Uni<List<GeocodingSearchResult>> primaryResult = callProviderForward(primaryProvider, query, biasCenter, limit);

        String fallbackProvider = configService.getFallbackProvider();
        if (!fallbackProvider.isEmpty() && !fallbackProvider.equalsIgnoreCase(primaryProvider)) {
            return primaryResult.onFailure().recoverWithUni(failure -> {
                log.warn("Primary provider '{}' forward search failed, trying fallback provider '{}'",
                        primaryProvider, fallbackProvider, failure);
                log.warn("Forward search failure details: type={}, message={}",
                        failure.getClass().getName(),
                        failure.getMessage());
                return callProviderForward(fallbackProvider, query, biasCenter, limit);
            });
        }

        return primaryResult;
    }

    private Uni<List<GeocodingSearchResult>> callProviderForward(String providerName, String query, Point biasCenter, int limit) {
        log.info("Calling {} with query {}", providerName, query);
        return switch (providerName.toLowerCase()) {
            case "nominatim" -> {
                if (!nominatimService.isEnabled()) {
                    yield Uni.createFrom().failure(new GeocodingException("Nominatim provider is disabled"));
                }
                yield nominatimService.forwardSearch(query, biasCenter, limit);
            }
            case "googlemaps" -> {
                if (!googleMapsService.isEnabled()) {
                    yield Uni.createFrom().failure(new GeocodingException("Google Maps provider is disabled or not configured"));
                }
                yield googleMapsService.forwardSearch(query, biasCenter, limit);
            }
            case "mapbox" -> {
                if (!mapboxService.isEnabled()) {
                    yield Uni.createFrom().failure(new GeocodingException("Mapbox provider is disabled or not configured"));
                }
                yield mapboxService.forwardSearch(query, biasCenter, limit);
            }
            case "photon" -> {
                if (!photonService.isEnabled()) {
                    yield Uni.createFrom().failure(new GeocodingException("Photon provider is disabled or not configured"));
                }
                yield photonService.forwardSearch(query, biasCenter, limit);
            }
            case "geoapify" -> {
                if (!geoapifyService.isEnabled()) {
                    yield Uni.createFrom().failure(new GeocodingException("Geoapify provider is disabled or not configured"));
                }
                yield geoapifyService.forwardSearch(query, biasCenter, limit);
            }
            case "chibigeo" -> {
                if (!chibiGeoService.isEnabled()) {
                    yield Uni.createFrom().failure(new GeocodingException("ChibiGeo provider is disabled or not configured"));
                }
                yield chibiGeoService.forwardSearch(query, biasCenter, limit);
            }
            default -> callCustomProviderForward(providerName, query, biasCenter, limit);
        };
    }

    private Uni<FormattableGeocodingResult> callCustomProvider(String providerName, Point requestCoordinates) {
        Optional<CustomGeocodingProviderEntity> provider = customProviderService.findEnabledByName(providerName);
        if (provider.isEmpty()) {
            log.error("Unknown provider: {}", providerName);
            return Uni.createFrom().failure(new GeocodingException("Unknown provider: " + providerName));
        }

        CustomGeocodingProviderEntity entity = provider.get();
        return switch (entity.getType().toLowerCase()) {
            case "photon" -> callCustomPhoton(entity, requestCoordinates);
            case "nominatim" -> callCustomNominatim(entity, requestCoordinates);
            default -> Uni.createFrom().failure(new GeocodingException("Unsupported custom provider type: " + entity.getType()));
        };
    }

    private Uni<List<GeocodingSearchResult>> callCustomProviderForward(String providerName, String query, Point biasCenter, int limit) {
        Optional<CustomGeocodingProviderEntity> provider = customProviderService.findEnabledByName(providerName);
        if (provider.isEmpty()) {
            return Uni.createFrom().failure(new GeocodingException("Unknown provider: " + providerName));
        }

        CustomGeocodingProviderEntity entity = provider.get();
        return switch (entity.getType().toLowerCase()) {
            case "photon" -> callCustomPhotonForward(entity, query, biasCenter, limit);
            case "nominatim" -> callCustomNominatimForward(entity, query, biasCenter, limit);
            default -> Uni.createFrom().failure(new GeocodingException("Unsupported custom provider type: " + entity.getType()));
        };
    }

    private Uni<FormattableGeocodingResult> callCustomPhoton(CustomGeocodingProviderEntity entity, Point requestCoordinates) {
        String language = sanitizePhotonLanguage(entity.getLanguage());
        return buildPhotonClient(entity)
                .getAddress(requestCoordinates.getX(), requestCoordinates.getY(), language, null)
                .map(response -> photonAdapter.adapt(response, requestCoordinates, providerDisplayName(entity)))
                .onItem().ifNull().failWith(() -> new GeocodingException(providerDisplayName(entity) + " adapter returned null result"))
                .onFailure().transform(failure -> new GeocodingException(providerDisplayName(entity) + " geocoding failed", failure));
    }

    private Uni<List<GeocodingSearchResult>> callCustomPhotonForward(CustomGeocodingProviderEntity entity, String query, Point biasCenter, int limit) {
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.length() < 2) {
            return Uni.createFrom().item(List.of());
        }

        String language = sanitizePhotonLanguage(entity.getLanguage());
        Double biasLat = biasCenter == null ? null : biasCenter.getY();
        Double biasLon = biasCenter == null ? null : biasCenter.getX();
        int safeLimit = Math.max(1, Math.min(limit, 20));

        return buildPhotonClient(entity)
                .search(safeQuery, safeLimit, biasLat, biasLon, biasCenter == null ? null : 12, language, language, null)
                .map(response -> mapCustomPhotonSearchResponse(response, biasCenter, providerDisplayName(entity)))
                .onFailure().transform(failure -> new GeocodingException(providerDisplayName(entity) + " forward search failed", failure));
    }

    private Uni<FormattableGeocodingResult> callCustomNominatim(CustomGeocodingProviderEntity entity, Point requestCoordinates) {
        return buildNominatimClient(entity)
                .getAddress("json", requestCoordinates.getX(), requestCoordinates.getY(), entity.getLanguage())
                .map(response -> nominatimAdapter.adapt(response, requestCoordinates, providerDisplayName(entity)))
                .onItem().ifNull().failWith(() -> new GeocodingException(providerDisplayName(entity) + " adapter returned null result"))
                .onFailure().transform(failure -> new GeocodingException(providerDisplayName(entity) + " geocoding failed", failure));
    }

    private Uni<List<GeocodingSearchResult>> callCustomNominatimForward(CustomGeocodingProviderEntity entity, String query, Point biasCenter, int limit) {
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.length() < 2) {
            return Uni.createFrom().item(List.of());
        }

        return buildNominatimClient(entity)
                .search("jsonv2", safeQuery, Math.max(1, Math.min(limit, 20)), 1, buildSearchViewbox(biasCenter), 0, entity.getLanguage())
                .map(results -> {
                    List<GeocodingSearchResult> mapped = new ArrayList<>();
                    if (results == null) {
                        return mapped;
                    }
                    for (var response : results) {
                        Point requestPoint = createNominatimRequestPoint(response, biasCenter);
                        if (requestPoint == null) {
                            continue;
                        }
                        FormattableGeocodingResult adapted = nominatimAdapter.adapt(response, requestPoint, providerDisplayName(entity));
                        Point resultPoint = adapted.getResultCoordinates() != null ? adapted.getResultCoordinates() : requestPoint;
                        mapped.add(GeocodingSearchResult.builder()
                                .title(adapted.getFormattedDisplayName())
                                .latitude(resultPoint.getY())
                                .longitude(resultPoint.getX())
                                .city(adapted.getCity())
                                .country(adapted.getCountry())
                                .providerName(providerDisplayName(entity))
                                .build());
                    }
                    return mapped;
                })
                .onFailure().transform(failure -> new GeocodingException(providerDisplayName(entity) + " forward search failed", failure));
    }

    private PhotonRestClient buildPhotonClient(CustomGeocodingProviderEntity entity) {
        return RestClientBuilder.newBuilder()
                .baseUri(URI.create(entity.getUrl()))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .property("microprofile.rest.client.disable.default.mapper", true)
                .register(new CustomHeadersRequestFilter(customProviderService.decryptHeaders(entity)))
                .build(PhotonRestClient.class);
    }

    private NominatimRestClient buildNominatimClient(CustomGeocodingProviderEntity entity) {
        Map<String, String> headers = new java.util.LinkedHashMap<>(customProviderService.decryptHeaders(entity));
        headers.putIfAbsent("User-Agent", nominatimUserAgent);
        return RestClientBuilder.newBuilder()
                .baseUri(URI.create(entity.getUrl()))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .property("microprofile.rest.client.disable.default.mapper", true)
                .register(new CustomHeadersRequestFilter(headers))
                .build(NominatimRestClient.class);
    }

    private String providerDisplayName(CustomGeocodingProviderEntity entity) {
        return entity.getDisplayName() == null || entity.getDisplayName().isBlank()
                ? entity.getName()
                : entity.getDisplayName();
    }

    private String sanitizePhotonLanguage(String configuredLanguage) {
        String sanitized = org.github.tess1o.geopulse.geocoding.service.external.PhotonLanguageValidator.sanitizeForPhoton(configuredLanguage);
        if (configuredLanguage != null && !configuredLanguage.isBlank() && sanitized == null) {
            log.warn("Custom Photon provider language '{}' is invalid; ignoring language parameter", configuredLanguage);
        }
        return sanitized;
    }

    private List<GeocodingSearchResult> mapCustomPhotonSearchResponse(PhotonResponse response, Point fallbackPoint, String providerName) {
        List<GeocodingSearchResult> mapped = new ArrayList<>();
        if (response == null || response.getFeatures() == null || response.getFeatures().isEmpty()) {
            return mapped;
        }

        for (PhotonResponse.Feature feature : response.getFeatures()) {
            Point requestPoint = createPhotonRequestPoint(feature, fallbackPoint);
            if (requestPoint == null) {
                continue;
            }
            PhotonResponse single = new PhotonResponse();
            single.setType(response.getType());
            single.setFeatures(List.of(feature));
            FormattableGeocodingResult adapted = photonAdapter.adapt(single, requestPoint, providerName);
            Point resultPoint = adapted.getResultCoordinates() != null ? adapted.getResultCoordinates() : requestPoint;
            mapped.add(GeocodingSearchResult.builder()
                    .title(adapted.getFormattedDisplayName())
                    .latitude(resultPoint.getY())
                    .longitude(resultPoint.getX())
                    .city(adapted.getCity())
                    .country(adapted.getCountry())
                    .providerName(providerName)
                    .build());
        }
        return mapped;
    }

    private Point createPhotonRequestPoint(PhotonResponse.Feature feature, Point fallbackPoint) {
        if (feature == null || feature.getGeometry() == null
                || feature.getGeometry().getLongitude() == null
                || feature.getGeometry().getLatitude() == null) {
            return fallbackPoint;
        }
        try {
            return GeoUtils.createPoint(feature.getGeometry().getLongitude(), feature.getGeometry().getLatitude());
        } catch (Exception ignored) {
            return fallbackPoint;
        }
    }

    private Point createNominatimRequestPoint(org.github.tess1o.geopulse.geocoding.model.nominatim.NominatimResponse response,
                                             Point fallback) {
        if (response != null && response.getLon() != null && response.getLat() != null) {
            try {
                return GeoUtils.createPoint(Double.parseDouble(response.getLon()), Double.parseDouble(response.getLat()));
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String buildSearchViewbox(Point biasCenter) {
        if (biasCenter == null) {
            return null;
        }
        double lon = biasCenter.getX();
        double lat = biasCenter.getY();
        return String.format("%.6f,%.6f,%.6f,%.6f",
                Math.max(-180.0, lon - 0.35),
                Math.max(-90.0, lat - 0.35),
                Math.min(180.0, lon + 0.35),
                Math.min(90.0, lat + 0.35));
    }
}
