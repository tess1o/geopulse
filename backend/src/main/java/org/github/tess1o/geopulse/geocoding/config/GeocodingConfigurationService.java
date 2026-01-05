package org.github.tess1o.geopulse.geocoding.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;

import java.util.Optional;

/**
 * Geocoding configuration service that provides dynamic settings management.
 *
 * Settings are stored in the database and can be modified via admin panel.
 * Falls back to environment variables if not set in database.
 */
@ApplicationScoped
@Slf4j
public class GeocodingConfigurationService {

    private final SystemSettingsService settingsService;

    @Inject
    public GeocodingConfigurationService(SystemSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * Get the primary geocoding provider name.
     * @return Provider name (nominatim, photon, googlemaps, mapbox)
     */
    public String getPrimaryProvider() {
        return settingsService.getString("geocoding.primary-provider");
    }

    /**
     * Get the fallback geocoding provider name.
     * @return Provider name or empty string if not configured
     */
    public String getFallbackProvider() {
        return settingsService.getString("geocoding.fallback-provider");
    }

    /**
     * Get the delay between geocoding requests in milliseconds.
     * @return Delay in milliseconds
     */
    public int getDelayMs() {
        return settingsService.getInteger("geocoding.delay-ms");
    }

    /**
     * Check if Nominatim provider is enabled.
     */
    public boolean isNominatimEnabled() {
        return settingsService.getBoolean("geocoding.nominatim.enabled");
    }

    /**
     * Get custom Nominatim URL if configured.
     * @return Custom URL or empty if using default
     */
    public Optional<String> getNominatimUrl() {
        String url = settingsService.getString("geocoding.nominatim.url");
        return url.isEmpty() ? Optional.empty() : Optional.of(url);
    }

    /**
     * Get Nominatim language preference for Accept-Language header.
     * @return Language code (e.g., "en-US", "de", "uk") or empty if not configured
     */
    public Optional<String> getNominatimLanguage() {
        String language = settingsService.getString("geocoding.nominatim.language");
        return language.isEmpty() ? Optional.empty() : Optional.of(language);
    }

    /**
     * Check if Photon provider is enabled.
     */
    public boolean isPhotonEnabled() {
        return settingsService.getBoolean("geocoding.photon.enabled");
    }

    /**
     * Get custom Photon URL if configured.
     * @return Custom URL or empty if using default
     */
    public Optional<String> getPhotonUrl() {
        String url = settingsService.getString("geocoding.photon.url");
        return url.isEmpty() ? Optional.empty() : Optional.of(url);
    }

    /**
     * Get Photon language preference for Accept-Language header.
     * @return Language code (e.g., "en-US", "de", "uk") or empty if not configured
     */
    public Optional<String> getPhotonLanguage() {
        String language = settingsService.getString("geocoding.photon.language");
        return language.isEmpty() ? Optional.empty() : Optional.of(language);
    }

    /**
     * Check if Google Maps provider is enabled.
     */
    public boolean isGoogleMapsEnabled() {
        return settingsService.getBoolean("geocoding.googlemaps.enabled");
    }

    /**
     * Get Google Maps API key (decrypted).
     * @return Decrypted API key or empty string if not set
     */
    public String getGoogleMapsApiKey() {
        return settingsService.getString("geocoding.googlemaps.api-key");
    }

    /**
     * Check if Mapbox provider is enabled.
     */
    public boolean isMapboxEnabled() {
        return settingsService.getBoolean("geocoding.mapbox.enabled");
    }

    /**
     * Get Mapbox access token (decrypted).
     * @return Decrypted access token or empty string if not set
     */
    public String getMapboxAccessToken() {
        return settingsService.getString("geocoding.mapbox.access-token");
    }
}
