package org.github.tess1o.geopulse.poi.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;

/**
 * POI discovery settings, stored in the database and editable from the admin panel.
 *
 * <p>Keys follow the {@code geopulse.poi.*} convention so the environment variable is the
 * predictable {@code GEOPULSE_POI_*}. Key names are declared as constants so a rename
 * cannot silently orphan a setting.
 */
@ApplicationScoped
public class PoiConfigurationService {

    public static final String KEY_ENABLED = "poi.enabled";
    public static final String KEY_WIKIDATA_ENDPOINT = "poi.wikidata.endpoint";
    public static final String KEY_COMMONS_ENDPOINT = "poi.commons.endpoint";
    public static final String KEY_USER_AGENT = "poi.user-agent";
    public static final String KEY_THUMB_WIDTH = "poi.commons.thumb-width";
    public static final String KEY_MAX_POIS = "poi.max-results";
    public static final String KEY_CACHE_TTL_DAYS = "poi.cache.ttl-days";
    public static final String KEY_IMAGE_CACHE_TTL_DAYS = "poi.cache.image-ttl-days";
    public static final String KEY_LANGUAGE = "poi.language";

    private final SystemSettingsService settings;

    @Inject
    public PoiConfigurationService(SystemSettingsService settings) {
        this.settings = settings;
    }

    public boolean isEnabled() {
        return settings.getBoolean(KEY_ENABLED);
    }

    /**
     * Wikidata Query Service base URL.
     *
     * <p>Read per call rather than bound at startup: the clients are built with
     * {@code RestClientBuilder} so an admin changing this takes effect without a restart.
     */
    public String getWikidataEndpoint() {
        return nonBlankOrDefault(settings.getString(KEY_WIKIDATA_ENDPOINT), "https://query.wikidata.org");
    }

    /** Wikimedia Commons API base URL. */
    public String getCommonsEndpoint() {
        return nonBlankOrDefault(settings.getString(KEY_COMMONS_ENDPOINT), "https://commons.wikimedia.org");
    }

    public String getUserAgent() {
        return nonBlankOrDefault(settings.getString(KEY_USER_AGENT),
                "GeoPulse/1.39.0 (+https://github.com/tess1o/geopulse)");
    }

    public int getThumbWidth() {
        return settings.getInteger(KEY_THUMB_WIDTH);
    }

    public int getMaxPois() {
        return settings.getInteger(KEY_MAX_POIS);
    }

    public int getCacheTtlDays() {
        return settings.getInteger(KEY_CACHE_TTL_DAYS);
    }

    public int getImageCacheTtlDays() {
        return settings.getInteger(KEY_IMAGE_CACHE_TTL_DAYS);
    }

    /** Content language for labels and descriptions, e.g. {@code en}. */
    public String getLanguage() {
        String language = settings.getString(KEY_LANGUAGE);
        if (language == null || language.isBlank()) {
            return "en";
        }
        // Wikidata language codes are simple tags; reject anything that could break the query.
        return language.matches("[A-Za-z-]{2,12}") ? language : "en";
    }

    /** A blank setting must fall back to the default, not produce an unparseable URI. */
    private static String nonBlankOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
