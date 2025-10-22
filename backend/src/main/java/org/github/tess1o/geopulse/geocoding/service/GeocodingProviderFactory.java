package org.github.tess1o.geopulse.geocoding.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfig;
import org.github.tess1o.geopulse.geocoding.exception.GeocodingException;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.locationtech.jts.geom.Point;

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
    private final GeocodingConfig geocodingConfig;

    @Inject
    public GeocodingProviderFactory(NominatimGeocodingService nominatimService,
                                    GoogleMapsGeocodingService googleMapsService,
                                    MapboxGeocodingService mapboxService,
                                    PhotonGeocodingService photonService,
                                    GeocodingConfig geocodingConfig) {
        this.nominatimService = nominatimService;
        this.googleMapsService = googleMapsService;
        this.mapboxService = mapboxService;
        this.photonService = photonService;
        this.geocodingConfig = geocodingConfig;
    }

    /**
     * Reverse geocode coordinates using primary provider with optional fallback.
     *
     * @param requestCoordinates The coordinates to reverse geocode
     * @return Structured geocoding result
     */
    public Uni<FormattableGeocodingResult> reverseGeocode(Point requestCoordinates) {
        log.debug("Reverse geocoding coordinates: lon={}, lat={} using primary provider: {}",
                requestCoordinates.getX(), requestCoordinates.getY(), geocodingConfig.provider().primary());

        // Try primary provider
        Uni<FormattableGeocodingResult> primaryResult = callProvider(geocodingConfig.provider().primary(), requestCoordinates);

        // If fallback is configured, try it on primary failure
        String fallbackProvider = geocodingConfig.provider().fallback().orElse("");

        if (!fallbackProvider.isEmpty() && !fallbackProvider.equalsIgnoreCase(geocodingConfig.provider().primary())) {
            return primaryResult.onFailure().call(failure -> {
                log.warn("Primary provider '{}' failed, trying fallback provider '{}'", geocodingConfig.provider().primary(), fallbackProvider, failure);
                return callProvider(fallbackProvider, requestCoordinates);
            });
        } else {
            // No fallback configured, just return primary result (success or failure)
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
            default -> {
                log.error("Unknown provider: {}", providerName);
                yield Uni.createFrom().failure(new GeocodingException("Unknown provider: " + providerName));
            }
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
        return enabled;
    }
}