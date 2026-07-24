package org.github.tess1o.geopulse.weather.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;

@ApplicationScoped
@Transactional
public class WeatherConfigurationService {

    public static final String PROVIDER_OPEN_METEO = "OPEN_METEO";
    public static final String ATTRIBUTION_URL = "https://open-meteo.com/";

    public static final String WEATHER_ENABLED = "weather.enabled";
    public static final String FORECAST_URL = "weather.open-meteo.forecast-url";
    public static final String ARCHIVE_URL = "weather.open-meteo.archive-url";
    public static final String API_KEY = "weather.open-meteo.api-key";
    public static final String ONGOING_ENABLED = "weather.ongoing.enabled";
    public static final String ONGOING_INTERVAL_MINUTES = "weather.ongoing.interval-minutes";
    public static final String BACKFILL_ENABLED = "weather.backfill.enabled";
    public static final String DAILY_REQUEST_LIMIT = "weather.quota.daily-request-limit";
    public static final String ONGOING_RESERVE = "weather.quota.ongoing-reserve";
    public static final String COORDINATE_PRECISION = "weather.coordinate-precision";
    public static final String FAILED_TARGET_RETRY_ENABLED = "weather.failed-target-retry.enabled";
    public static final String FAILED_TARGET_RETRY_COOLDOWN_HOURS = "weather.failed-target-retry.cooldown-hours";

    @Inject
    SystemSettingsService settingsService;

    public boolean isEnabled() {
        return settingsService.getBoolean(WEATHER_ENABLED);
    }

    public boolean isConfigured() {
        return !forecastUrl().isBlank() && !archiveUrl().isBlank();
    }

    public String forecastUrl() {
        return normalizeBaseUrl(settingsService.getString(FORECAST_URL));
    }

    public String archiveUrl() {
        return normalizeBaseUrl(settingsService.getString(ARCHIVE_URL));
    }

    public String apiKey() {
        return settingsService.getString(API_KEY).trim();
    }

    public boolean ongoingEnabled() {
        return settingsService.getBoolean(ONGOING_ENABLED);
    }

    public int ongoingIntervalMinutes() {
        return Math.max(30, settingsService.getInteger(ONGOING_INTERVAL_MINUTES));
    }

    public boolean backfillEnabled() {
        return settingsService.getBoolean(BACKFILL_ENABLED);
    }

    public int dailyRequestLimit() {
        return Math.max(0, settingsService.getInteger(DAILY_REQUEST_LIMIT));
    }

    public int ongoingReserve() {
        return Math.max(0, settingsService.getInteger(ONGOING_RESERVE));
    }

    public int coordinatePrecision() {
        int value = settingsService.getInteger(COORDINATE_PRECISION);
        return Math.min(5, Math.max(0, value));
    }

    public boolean failedTargetRetryEnabled() {
        return settingsService.getBoolean(FAILED_TARGET_RETRY_ENABLED);
    }

    public int failedTargetRetryCooldownHours() {
        return Math.max(1, settingsService.getInteger(FAILED_TARGET_RETRY_COOLDOWN_HOURS));
    }

    public double bucketCoordinate(double value) {
        double factor = Math.pow(10, coordinatePrecision());
        return Math.round(value * factor) / factor;
    }

    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
