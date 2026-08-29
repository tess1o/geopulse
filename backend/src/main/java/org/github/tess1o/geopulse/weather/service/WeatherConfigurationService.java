package org.github.tess1o.geopulse.weather.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@ApplicationScoped
@Transactional
public class WeatherConfigurationService {

    public static final String PROVIDER_OPEN_METEO = "OPEN_METEO";
    public static final String PROVIDER_PIRATE_WEATHER = "PIRATE_WEATHER";
    public static final String OPEN_METEO_ATTRIBUTION_URL = "https://open-meteo.com/";
    public static final String PIRATE_WEATHER_ATTRIBUTION_URL = "https://pirateweather.net/";

    public static final String WEATHER_ENABLED = "weather.enabled";
    public static final String PRIMARY_PROVIDER = "weather.primary-provider";
    public static final String SECONDARY_PROVIDER = "weather.secondary-provider";
    public static final String OPEN_METEO_ENABLED = "weather.open-meteo.enabled";
    public static final String FORECAST_URL = "weather.open-meteo.forecast-url";
    public static final String ARCHIVE_URL = "weather.open-meteo.archive-url";
    public static final String API_KEY = "weather.open-meteo.api-key";
    public static final String PIRATE_ENABLED = "weather.pirate.enabled";
    public static final String PIRATE_BASE_URL = "weather.pirate.base-url";
    public static final String PIRATE_TIME_MACHINE_URL = "weather.pirate.time-machine-url";
    public static final String PIRATE_API_KEY = "weather.pirate.api-key";
    public static final String ONGOING_ENABLED = "weather.ongoing.enabled";
    public static final String ONGOING_INTERVAL_MINUTES = "weather.ongoing.interval-minutes";
    public static final String BACKFILL_ENABLED = "weather.backfill.enabled";
    public static final String DAILY_REQUEST_LIMIT = "weather.quota.daily-request-limit";
    public static final String ONGOING_RESERVE = "weather.quota.ongoing-reserve";
    public static final String COORDINATE_PRECISION = "weather.coordinate-precision";
    public static final String FAILED_TARGET_RETRY_ENABLED = "weather.failed-target-retry.enabled";
    public static final String FAILED_TARGET_RETRY_COOLDOWN_HOURS = "weather.failed-target-retry.cooldown-hours";
    public static final String OPEN_METEO_CONNECT_TIMEOUT_SECONDS = "weather.open-meteo.connect-timeout-seconds";
    public static final String OPEN_METEO_READ_TIMEOUT_SECONDS = "weather.open-meteo.read-timeout-seconds";
    public static final String PIRATE_CONNECT_TIMEOUT_SECONDS = "weather.pirate.connect-timeout-seconds";
    public static final String PIRATE_READ_TIMEOUT_SECONDS = "weather.pirate.read-timeout-seconds";
    public static final String TARGETS_COMPLETED_RETENTION_DAYS = "weather.targets.completed-retention-days";
    public static final String TARGETS_FAILED_RETENTION_DAYS = "weather.targets.failed-retention-days";
    public static final String TARGETS_IN_PROGRESS_TIMEOUT_MINUTES = "weather.targets.in-progress-timeout-minutes";

    @Inject
    SystemSettingsService settingsService;

    public boolean isEnabled() {
        return settingsService.getBoolean(WEATHER_ENABLED);
    }

    public boolean isConfigured() {
        return isProviderEnabledAndConfigured(primaryProvider());
    }

    public String primaryProvider() {
        String configured = normalizeProviderKey(settingsService.getString(PRIMARY_PROVIDER));
        return isKnownProvider(configured) ? configured : PROVIDER_OPEN_METEO;
    }

    public String secondaryProvider() {
        String configured = normalizeProviderKey(settingsService.getString(SECONDARY_PROVIDER));
        if (!isKnownProvider(configured) || configured.equals(primaryProvider())) {
            return "";
        }
        return configured;
    }

    public List<String> enabledConfiguredProviders() {
        return providerOrder(null);
    }

    public List<String> providerOrder(String requestedProvider) {
        Set<String> providers = new LinkedHashSet<>();
        addIfEnabledConfigured(providers, normalizeProviderKey(requestedProvider));
        addIfEnabledConfigured(providers, primaryProvider());
        addIfEnabledConfigured(providers, secondaryProvider());
        return List.copyOf(providers);
    }

    public boolean isProviderEnabled(String provider) {
        return switch (normalizeProviderKey(provider)) {
            case PROVIDER_OPEN_METEO -> settingsService.getBoolean(OPEN_METEO_ENABLED);
            case PROVIDER_PIRATE_WEATHER -> settingsService.getBoolean(PIRATE_ENABLED);
            default -> false;
        };
    }

    public boolean isProviderConfigured(String provider) {
        return switch (normalizeProviderKey(provider)) {
            case PROVIDER_OPEN_METEO -> !forecastUrl().isBlank() && !archiveUrl().isBlank();
            case PROVIDER_PIRATE_WEATHER -> !pirateBaseUrl().isBlank()
                    && !pirateTimeMachineUrl().isBlank()
                    && !pirateApiKey().isBlank();
            default -> false;
        };
    }

    public boolean isProviderEnabledAndConfigured(String provider) {
        return isProviderEnabled(provider) && isProviderConfigured(provider);
    }

    public String attributionUrl(String provider) {
        return PROVIDER_PIRATE_WEATHER.equals(normalizeProviderKey(provider))
                ? PIRATE_WEATHER_ATTRIBUTION_URL
                : OPEN_METEO_ATTRIBUTION_URL;
    }

    public String normalizeProviderKey(String provider) {
        if (provider == null || provider.isBlank()) {
            return "";
        }
        return provider.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    public boolean isKnownProvider(String provider) {
        String normalized = normalizeProviderKey(provider);
        return PROVIDER_OPEN_METEO.equals(normalized) || PROVIDER_PIRATE_WEATHER.equals(normalized);
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

    public String pirateBaseUrl() {
        return normalizeBaseUrl(settingsService.getString(PIRATE_BASE_URL));
    }

    public String pirateTimeMachineUrl() {
        return normalizeBaseUrl(settingsService.getString(PIRATE_TIME_MACHINE_URL));
    }

    public String pirateApiKey() {
        return settingsService.getString(PIRATE_API_KEY).trim();
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

    public int openMeteoConnectTimeoutSeconds() {
        return Math.max(1, settingsService.getInteger(OPEN_METEO_CONNECT_TIMEOUT_SECONDS));
    }

    public int openMeteoReadTimeoutSeconds() {
        return Math.max(1, settingsService.getInteger(OPEN_METEO_READ_TIMEOUT_SECONDS));
    }

    public int pirateConnectTimeoutSeconds() {
        return Math.max(1, settingsService.getInteger(PIRATE_CONNECT_TIMEOUT_SECONDS));
    }

    public int pirateReadTimeoutSeconds() {
        return Math.max(1, settingsService.getInteger(PIRATE_READ_TIMEOUT_SECONDS));
    }

    public int completedTargetRetentionDays() {
        return Math.max(1, settingsService.getInteger(TARGETS_COMPLETED_RETENTION_DAYS));
    }

    public int failedTargetRetentionDays() {
        return Math.max(1, settingsService.getInteger(TARGETS_FAILED_RETENTION_DAYS));
    }

    public int inProgressTargetTimeoutMinutes() {
        return Math.max(1, settingsService.getInteger(TARGETS_IN_PROGRESS_TIMEOUT_MINUTES));
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

    private void addIfEnabledConfigured(Set<String> providers, String provider) {
        if (isProviderEnabledAndConfigured(provider)) {
            providers.add(provider);
        }
    }
}
