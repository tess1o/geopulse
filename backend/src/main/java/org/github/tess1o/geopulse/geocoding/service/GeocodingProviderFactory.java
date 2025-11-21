package org.github.tess1o.geopulse.geocoding.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfigurationService;
import org.github.tess1o.geopulse.geocoding.exception.GeocodingException;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.service.external.GoogleMapsGeocodingService;
import org.github.tess1o.geopulse.geocoding.service.external.MapboxGeocodingService;
import org.github.tess1o.geopulse.geocoding.service.external.NominatimGeocodingService;
import org.github.tess1o.geopulse.geocoding.service.external.PhotonGeocodingService;
import org.locationtech.jts.geom.Point;

import java.util.Optional;

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
    private final GeocodingConfigurationService configService;

    @Inject
    public GeocodingProviderFactory(NominatimGeocodingService nominatimService,
                                    GoogleMapsGeocodingService googleMapsService,
                                    MapboxGeocodingService mapboxService,
                                    PhotonGeocodingService photonService,
                                    GeocodingConfigurationService configService) {
        this.nominatimService = nominatimService;
        this.googleMapsService = googleMapsService;
        this.mapboxService = mapboxService;
        this.photonService = photonService;
        this.configService = configService;
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
}