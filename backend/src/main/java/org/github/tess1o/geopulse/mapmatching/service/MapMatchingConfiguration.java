package org.github.tess1o.geopulse.mapmatching.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;

@ApplicationScoped
public class MapMatchingConfiguration {
    private static final String ENABLED = "map-matching.enabled";
    private static final String PROVIDER = "map-matching.provider";
    private static final String AUTOMATIC_ENABLED = "map-matching.automatic.enabled";
    private static final String BACKFILL_ENABLED = "map-matching.backfill.enabled";
    private static final String QUIET_PERIOD_MINUTES = "map-matching.automatic.quiet-period-minutes";
    private static final String VALHALLA_BASE_URL = "map-matching.valhalla.base-url";
    private static final String CONNECT_TIMEOUT_SECONDS = "map-matching.valhalla.connect-timeout-seconds";
    private static final String READ_TIMEOUT_SECONDS = "map-matching.valhalla.read-timeout-seconds";
    private static final String MAX_INPUT_POINTS = "map-matching.max-input-points";
    private static final String MAX_TRIP_DURATION_HOURS = "map-matching.max-trip-duration-hours";
    private static final String WORKER_BATCH_SIZE = "map-matching.worker.batch-size";
    private static final String MAX_ATTEMPTS = "map-matching.max-attempts";

    private final SystemSettingsService settingsService;

    public MapMatchingConfiguration(SystemSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public boolean isEnabled() {
        return settingsService.getBoolean(ENABLED);
    }

    public String provider() {
        String provider = settingsService.getString(PROVIDER);
        return provider == null || provider.isBlank() ? "valhalla" : provider.trim().toLowerCase();
    }

    public boolean automaticEnabled() {
        return settingsService.getBoolean(AUTOMATIC_ENABLED);
    }

    public boolean backfillEnabled() {
        return settingsService.getBoolean(BACKFILL_ENABLED);
    }

    public int quietPeriodMinutes() {
        return Math.max(1, settingsService.getInteger(QUIET_PERIOD_MINUTES));
    }

    public boolean valhallaConfigured() {
        return !settingsService.getString(VALHALLA_BASE_URL).isBlank();
    }

    public String valhallaBaseUrl() {
        String baseUrl = settingsService.getString(VALHALLA_BASE_URL).trim();
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("Valhalla base URL is not configured");
        }
        return baseUrl;
    }

    public int getConnectTimeoutSeconds() {
        return settingsService.getInteger(CONNECT_TIMEOUT_SECONDS);
    }

    public int getReadTimeoutSeconds() {
        return settingsService.getInteger(READ_TIMEOUT_SECONDS);
    }

    public int getMaxInputPoints() {
        return settingsService.getInteger(MAX_INPUT_POINTS);
    }

    public int getMaxTripDurationHours() {
        return settingsService.getInteger(MAX_TRIP_DURATION_HOURS);
    }

    public int getWorkerBatchSize() {
        return settingsService.getInteger(WORKER_BATCH_SIZE);
    }

    public int getMaxAttempts() {
        return settingsService.getInteger(MAX_ATTEMPTS);
    }

    public String configHashSource() {
        return "algorithm=v3|" + provider() + "|" + valhallaBaseUrl() + "|"
                + Math.max(1, getMaxInputPoints()) + "|" + Math.max(1, getMaxTripDurationHours());
    }
}
