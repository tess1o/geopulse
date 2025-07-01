package org.github.tess1o.geopulse.geocoding.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfig;

import java.util.List;
import java.util.Optional;

/**
 * Startup validation for geocoding provider configuration.
 * Ensures at least one provider is enabled and warns about configuration issues.
 */
@ApplicationScoped
@Slf4j
public class GeocodingProviderValidation {

    private final GeocodingProviderFactory providerFactory;
    private final GeocodingConfig config;

    @Inject
    public GeocodingProviderValidation(GeocodingProviderFactory providerFactory, GeocodingConfig config) {
        this.providerFactory = providerFactory;
        this.config = config;
    }

    /**
     * Validate geocoding provider configuration at startup.
     */
    void onStart(@Observes StartupEvent ev) {
        log.info("Validating geocoding provider configuration...");

        // Get actually enabled providers (including credential checks)
        List<String> enabledProviders = providerFactory.getEnabledProviders();

        if (enabledProviders.isEmpty()) {
            log.error("GEOCODING CONFIGURATION ERROR: No geocoding providers are enabled and properly configured!");
            log.error("Please enable at least one provider:");
            log.error("Nominatim: set geocoding.provider.nominatim.enabled=true");
            log.error("Google Maps: set geocoding.provider.googlemaps.enabled=true and provide geocoding.googlemaps.api-key");
            log.error("Mapbox: set geocoding.provider.mapbox.enabled=true and provide geocoding.mapbox.access-token");
            throw new IllegalStateException("No geocoding providers are enabled and properly configured");
        }

        // Check if primary provider is actually enabled
        String primaryProvider = config.provider().primary();
        boolean primaryProviderEnabled = enabledProviders.stream()
                .anyMatch(provider -> provider.equalsIgnoreCase(primaryProvider));

        if (!primaryProviderEnabled) {
            log.error("PRIMARY PROVIDER ERROR: Configured primary provider '{}' is not enabled or properly configured", primaryProvider);
            log.error("Available enabled providers: {}", enabledProviders);
            throw new IllegalStateException("Primary provider '" + primaryProvider + "' is not enabled or properly configured");
        }

        // Check fallback provider if configured
        Optional<String> fallbackProviderOpt = config.provider().fallback();
        boolean fallbackProviderEnabled = true;
        if (fallbackProviderOpt.isPresent()) {
            String fallbackProvider = fallbackProviderOpt.get();
            fallbackProviderEnabled = enabledProviders.stream()
                    .anyMatch(provider -> provider.equalsIgnoreCase(fallbackProvider));

            if (!fallbackProviderEnabled) {
                log.warn("FALLBACK PROVIDER WARNING: Configured fallback provider '{}' is not enabled or properly configured", fallbackProvider);
                log.warn("Fallback will not be available if primary provider fails");
            }
        }

        // Log configuration status
        log.info("Geocoding provider configuration:");
        log.info("Primary provider: {} (ENABLED)", primaryProvider);
        if (fallbackProviderOpt.isPresent()) {
            log.info("Fallback provider: {} ({})", fallbackProviderOpt.get(), fallbackProviderEnabled ? "ENABLED" : "DISABLED/MISCONFIGURED");
        } else {
            log.info("Fallback provider: none configured");
        }
        log.info("Available providers: {}", enabledProviders);
        log.info("Retry configuration: @Retry(maxRetries=3, delay=500ms, jitter=100ms)");
        log.info("Rate limiting: @Bulkhead - Nominatim: 1 concurrent, Google Maps: 5 concurrent, Mapbox: 3 concurrent");
        log.info("Circuit breaker: @CircuitBreaker - Nominatim: 50%/4req/30s, Google Maps: 60%/10req/60s, Mapbox: 55%/6req/45s");

        // Provider-specific warnings
        if (config.provider().nominatim().enabled()) {
            log.info("Nominatim: enabled");
        }

        if (config.provider().googlemaps().enabled()) {
            if (config.googlemaps().apiKey().isEmpty()) {
                log.warn("Google Maps: enabled but API key not configured - provider will be unavailable");
            } else {
                log.info("Google Maps: enabled with API key");
            }
        }

        if (config.provider().mapbox().enabled()) {
            if (config.mapbox().accessToken().isEmpty()) {
                log.warn("Mapbox: enabled but access token not configured - provider will be unavailable");
            } else {
                log.info("Mapbox: enabled with access token");
            }
        }

        log.info("Geocoding provider validation completed successfully");
    }
}