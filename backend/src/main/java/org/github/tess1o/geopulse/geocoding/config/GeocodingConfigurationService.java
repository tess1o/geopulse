package org.github.tess1o.geopulse.geocoding.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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
@Transactional(Transactional.TxType.REQUIRES_NEW)
@Slf4j
public class GeocodingConfigurationService {

    private final SystemSettingsService settingsService;

    @Inject
    public GeocodingConfigurationService(SystemSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * Get the primary geocoding provider name.
     * @return Provider name (nominatim, photon, googlemaps, mapbox, geoapify, chibigeo)
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
     * Get delay between requests for a specific provider.
     */
    public int getDelayMsForProvider(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return getDelayMs();
        }

        return switch (providerName.toLowerCase()) {
            case "geoapify" -> settingsService.getInteger("geocoding.geoapify.delay-ms");
            case "chibigeo" -> settingsService.getInteger("geocoding.chibigeo.delay-ms");
            default -> getDelayMs();
        };
    }

    /**
     * Get delay between requests for the currently configured primary provider.
     */
    public int getPrimaryProviderDelayMs() {
        return getDelayMsForProvider(getPrimaryProvider());
    }

    /**
     * Check if Nominatim provider is enabled.
     */
    public boolean isNominatimEnabled() {
        return settingsService.getBoolean("geocoding.nominatim.enabled");
    }

    /**
     * Check if Nominatim forward search is allowed for the public nominatim.openstreetmap.org host.
     */
    public boolean isNominatimPublicHostForwardSearchEnabled() {
        return settingsService.getBoolean("geocoding.nominatim.public-host-forward-search-enabled");
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
     * Get Google Maps language preference for reverse geocoding.
     * @return Language code (e.g., "en", "uk", "pt-BR") or empty if not configured
     */
    public Optional<String> getGoogleMapsLanguage() {
        String language = settingsService.getString("geocoding.googlemaps.language");
        return language.isEmpty() ? Optional.empty() : Optional.of(language);
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

    /**
     * Check if Geoapify provider is enabled.
     */
    public boolean isGeoapifyEnabled() {
        return settingsService.getBoolean("geocoding.geoapify.enabled");
    }

    /**
     * Get Geoapify API key (decrypted).
     * @return Decrypted API key or empty string if not set
     */
    public String getGeoapifyApiKey() {
        return settingsService.getString("geocoding.geoapify.api-key");
    }

    /**
     * Get Geoapify language preference.
     */
    public Optional<String> getGeoapifyLanguage() {
        String language = settingsService.getString("geocoding.geoapify.language");
        return optionalText(language);
    }

    /**
     * Check if ChibiGeo provider is enabled.
     */
    public boolean isChibiGeoEnabled() {
        return settingsService.getBoolean("geocoding.chibigeo.enabled");
    }

    /**
     * Get custom ChibiGeo URL if configured.
     */
    public Optional<String> getChibiGeoUrl() {
        String url = settingsService.getString("geocoding.chibigeo.url");
        return url.isEmpty() ? Optional.empty() : Optional.of(url);
    }

    /**
     * Get ChibiGeo API key (decrypted).
     */
    public String getChibiGeoApiKey() {
        return settingsService.getString("geocoding.chibigeo.api-key");
    }

    /**
     * Get ChibiGeo language preference.
     */
    public Optional<String> getChibiGeoLanguage() {
        String language = settingsService.getString("geocoding.chibigeo.language");
        return optionalText(language);
    }

    private Optional<String> optionalText(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || trimmed.equals("\"\"") ? Optional.empty() : Optional.of(trimmed);
    }
}
