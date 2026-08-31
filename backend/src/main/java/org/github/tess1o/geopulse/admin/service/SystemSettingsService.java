package org.github.tess1o.geopulse.admin.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.github.tess1o.geopulse.admin.model.SettingDefinition;
import org.github.tess1o.geopulse.admin.model.SettingInfo;
import org.github.tess1o.geopulse.admin.model.SystemSettingsEntity;
import org.github.tess1o.geopulse.admin.model.ValueType;
import org.github.tess1o.geopulse.admin.repository.SystemSettingsRepository;
import org.github.tess1o.geopulse.ai.service.AIEncryptionService;
import org.github.tess1o.geopulse.shared.system.ProcessIdentity;
import org.github.tess1o.geopulse.mapmatching.event.MapMatchingSettingsChangedEvent;
import org.github.tess1o.geopulse.user.model.DistanceUnit;
import org.github.tess1o.geopulse.user.model.TemperatureUnit;
import org.github.tess1o.geopulse.weather.event.WeatherSettingsChangedEvent;

import java.time.Instant;
import java.util.*;

/**
 * Service for managing system settings with environment variable fallback.
 * <p>
 * Pattern: Check DB first, fall back to env var if not found.
 * Similar to TimelineConfigurationProvider pattern.
 */
@ApplicationScoped
@Slf4j
public class SystemSettingsService {

    private final SystemSettingsRepository repository;
    private final Config config;
    private final AIEncryptionService encryptionService;
    private final Event<WeatherSettingsChangedEvent> weatherSettingsChangedEvent;
    private final Event<MapMatchingSettingsChangedEvent> mapMatchingSettingsChangedEvent;

    private static final String IMPORT_DROP_FOLDER_IDENTITY_KEY = "import.drop-folder.runtime-identity";
    private static final String DEFAULT_DISTANCE_UNIT_KEY = "system.user.default-distance-unit";
    private static final String DEFAULT_TEMPERATURE_UNIT_KEY = "system.user.default-temperature-unit";

    // Mapping from setting keys to their env var names and defaults
    private static final Map<String, SettingDefinition> SETTING_DEFINITIONS = new LinkedHashMap<>();

    static {
        // Authentication settings
        SETTING_DEFINITIONS.put("auth.registration.enabled",
                new SettingDefinition("geopulse.auth.registration.enabled", "true", ValueType.BOOLEAN, "auth", "Enable/disable all registration"));
        SETTING_DEFINITIONS.put("auth.password-registration.enabled",
                new SettingDefinition("geopulse.auth.password-registration.enabled", "true", ValueType.BOOLEAN, "auth", "Enable/disable password registration"));
        SETTING_DEFINITIONS.put("auth.oidc.registration.enabled",
                new SettingDefinition("geopulse.auth.oidc.registration.enabled", "true", ValueType.BOOLEAN, "auth", "Enable/disable OIDC registration"));
        SETTING_DEFINITIONS.put("auth.oidc.auto-link-accounts",
                new SettingDefinition("geopulse.oidc.auto-link-accounts", "false", ValueType.BOOLEAN, "auth", "Auto-link OIDC accounts by email"));
        SETTING_DEFINITIONS.put("auth.oidc.callback-base-url",
                new SettingDefinition("geopulse.oidc.callback-base-url", "", ValueType.STRING, "auth", "Base URL used to build OIDC callback URLs"));
        SETTING_DEFINITIONS.put("auth.oidc.jwks-cache.ttl-hours",
                new SettingDefinition("geopulse.oidc.jwks-cache.ttl-hours", "24", ValueType.INTEGER, "auth", "Hours to cache OIDC provider signing keys"));
        SETTING_DEFINITIONS.put("auth.oidc.cleanup.session-states.enabled",
                new SettingDefinition("geopulse.oidc.cleanup.session-states.enabled", "true", ValueType.BOOLEAN, "auth", "Clean up expired OIDC session state records"));
        SETTING_DEFINITIONS.put("auth.login.enabled",
                new SettingDefinition("geopulse.auth.login.enabled", "true", ValueType.BOOLEAN, "auth", "Enable/disable all login"));
        SETTING_DEFINITIONS.put("auth.password-login.enabled",
                new SettingDefinition("geopulse.auth.password-login.enabled", "true", ValueType.BOOLEAN, "auth", "Enable/disable password login"));
        SETTING_DEFINITIONS.put("auth.oidc.login.enabled",
                new SettingDefinition("geopulse.auth.oidc.login.enabled", "true", ValueType.BOOLEAN, "auth", "Enable/disable OIDC login"));
        SETTING_DEFINITIONS.put("auth.admin-login-bypass.enabled",
                new SettingDefinition("geopulse.auth.admin-login-bypass.enabled", "true", ValueType.BOOLEAN, "auth", "Allow admins to bypass login restrictions"));
        SETTING_DEFINITIONS.put("auth.guest-root-redirect-to-login.enabled",
                new SettingDefinition("geopulse.auth.guest-root-redirect-to-login.enabled", "false", ValueType.BOOLEAN, "auth", "Redirect signed-out users from / to /login"));

        // Geocoding settings - General
        SETTING_DEFINITIONS.put("geocoding.primary-provider",
                new SettingDefinition("geocoding.provider.primary", "nominatim", ValueType.STRING, "geocoding", "Primary geocoding provider"));
        SETTING_DEFINITIONS.put("geocoding.fallback-provider",
                new SettingDefinition("geocoding.provider.fallback", "", ValueType.STRING, "geocoding", "Fallback geocoding provider (optional)"));
        SETTING_DEFINITIONS.put("geocoding.delay-ms",
                new SettingDefinition("geocoding.provider.delay.ms", "1000", ValueType.INTEGER, "geocoding", "Delay between geocoding requests (milliseconds)"));
        SETTING_DEFINITIONS.put("geocoding.geoapify.delay-ms",
                new SettingDefinition("geocoding.provider.geoapify.delay.ms", "0", ValueType.INTEGER, "geocoding", "Delay between Geoapify requests (milliseconds)"));
        SETTING_DEFINITIONS.put("geocoding.chibigeo.delay-ms",
                new SettingDefinition("geocoding.provider.chibigeo.delay.ms", "0", ValueType.INTEGER, "geocoding", "Delay between ChibiGeo requests (milliseconds)"));

        // Geocoding settings - Provider Availability
        SETTING_DEFINITIONS.put("geocoding.nominatim.enabled",
                new SettingDefinition("geocoding.provider.nominatim.enabled", "true", ValueType.BOOLEAN, "geocoding", "Enable Nominatim geocoding provider"));
        SETTING_DEFINITIONS.put("geocoding.nominatim.public-host-forward-search-enabled",
                new SettingDefinition("geocoding.nominatim.public-host-forward-search-enabled", "false", ValueType.BOOLEAN, "geocoding", "Allow Nominatim forward search on public nominatim.openstreetmap.org (self-hosted remains allowed)"));
        SETTING_DEFINITIONS.put("geocoding.nominatim.url",
                new SettingDefinition("quarkus.rest-client.nominatim-api.url", "", ValueType.STRING, "geocoding", "Custom Nominatim server URL (optional)"));
        SETTING_DEFINITIONS.put("geocoding.nominatim.language",
                new SettingDefinition("geocoding.nominatim.language", "", ValueType.STRING, "geocoding", "Nominatim language preference (BCP 47: en-US, de, uk, ja)"));

        SETTING_DEFINITIONS.put("geocoding.photon.enabled",
                new SettingDefinition("geocoding.provider.photon.enabled", "false", ValueType.BOOLEAN, "geocoding", "Enable Photon geocoding provider"));
        SETTING_DEFINITIONS.put("geocoding.photon.url",
                new SettingDefinition("quarkus.rest-client.photon-api.url", "", ValueType.STRING, "geocoding", "Custom Photon server URL (optional)"));
        SETTING_DEFINITIONS.put("geocoding.photon.language",
                new SettingDefinition("geocoding.photon.language", "", ValueType.STRING, "geocoding", "Photon language preference (allowed: de, pl, el, en, es, fa, fr, it, ja, ko; empty = provider default)"));

        SETTING_DEFINITIONS.put("geocoding.googlemaps.enabled",
                new SettingDefinition("geocoding.provider.googlemaps.enabled", "false", ValueType.BOOLEAN, "geocoding", "Enable Google Maps geocoding provider"));
        SETTING_DEFINITIONS.put("geocoding.googlemaps.api-key",
                new SettingDefinition("geocoding.provider.googlemaps.api-key", "", ValueType.ENCRYPTED, "geocoding", "Google Maps API key (encrypted)"));
        SETTING_DEFINITIONS.put("geocoding.googlemaps.language",
                new SettingDefinition("geocoding.provider.googlemaps.language", "", ValueType.STRING, "geocoding", "Google Maps language preference (supported Google language code, e.g., en, uk, pt-BR, zh-CN)"));

        SETTING_DEFINITIONS.put("geocoding.mapbox.enabled",
                new SettingDefinition("geocoding.provider.mapbox.enabled", "false", ValueType.BOOLEAN, "geocoding", "Enable Mapbox geocoding provider"));
        SETTING_DEFINITIONS.put("geocoding.mapbox.access-token",
                new SettingDefinition("geocoding.mapbox.access-token", "", ValueType.ENCRYPTED, "geocoding", "Mapbox access token (encrypted)"));

        SETTING_DEFINITIONS.put("geocoding.geoapify.enabled",
                new SettingDefinition("geocoding.provider.geoapify.enabled", "false", ValueType.BOOLEAN, "geocoding", "Enable Geoapify geocoding provider"));
        SETTING_DEFINITIONS.put("geocoding.geoapify.api-key",
                new SettingDefinition("geocoding.provider.geoapify.api-key", "", ValueType.ENCRYPTED, "geocoding", "Geoapify API key (encrypted)"));
        SETTING_DEFINITIONS.put("geocoding.geoapify.language",
                new SettingDefinition("geocoding.provider.geoapify.language", "", ValueType.STRING, "geocoding", "Geoapify language preference (optional)"));

        SETTING_DEFINITIONS.put("geocoding.chibigeo.enabled",
                new SettingDefinition("geocoding.provider.chibigeo.enabled", "false", ValueType.BOOLEAN, "geocoding", "Enable ChibiGeo geocoding provider"));
        SETTING_DEFINITIONS.put("geocoding.chibigeo.url",
                new SettingDefinition("quarkus.rest-client.chibigeo-api.url", "", ValueType.STRING, "geocoding", "Custom ChibiGeo Photon-compatible server URL (optional)"));
        SETTING_DEFINITIONS.put("geocoding.chibigeo.api-key",
                new SettingDefinition("geocoding.provider.chibigeo.api-key", "", ValueType.ENCRYPTED, "geocoding", "ChibiGeo API key (encrypted)"));
        SETTING_DEFINITIONS.put("geocoding.chibigeo.language",
                new SettingDefinition("geocoding.provider.chibigeo.language", "", ValueType.STRING, "geocoding", "ChibiGeo language preference (allowed Photon codes; empty = provider default)"));
        SETTING_DEFINITIONS.put("geocoding.cache.max-bbox-area-km2",
                new SettingDefinition("geocoding.cache.max-bbox-area-km2", "5000", ValueType.INTEGER, "geocoding", "Maximum provider bounding box area accepted for cache matching"));
        SETTING_DEFINITIONS.put("geocoding.reconcile.item.max-attempts",
                new SettingDefinition("geocoding.reconcile.item.max-attempts", "4", ValueType.INTEGER, "geocoding", "Maximum attempts when reconciling one cached geocoding record"));
        SETTING_DEFINITIONS.put("geocoding.reconcile.circuit-open-wait-ms",
                new SettingDefinition("geocoding.reconcile.circuit-open-wait.ms", "20000", ValueType.INTEGER, "geocoding", "Milliseconds to wait when a provider circuit is open during reconciliation"));
        SETTING_DEFINITIONS.put("geocoding.reconcile.inter-item-delay-ms",
                new SettingDefinition("geocoding.reconcile.inter-item-delay.ms", "1000", ValueType.INTEGER, "geocoding", "Delay between reconciled geocoding records in milliseconds"));

        // GPS processing defaults
        SETTING_DEFINITIONS.put("gps.filter.inaccurate-data.enabled",
                new SettingDefinition("geopulse.gps.filter.inaccurate-data.enabled", "false", ValueType.BOOLEAN, "gps", "Default: filter inaccurate GPS data"));
        SETTING_DEFINITIONS.put("gps.max-allowed-accuracy",
                new SettingDefinition("geopulse.gps.max-allowed-accuracy", "100", ValueType.INTEGER, "gps", "Max allowed accuracy (meters)"));
        SETTING_DEFINITIONS.put("gps.max-allowed-speed",
                new SettingDefinition("geopulse.gps.max-allowed-speed", "250", ValueType.INTEGER, "gps", "Max allowed speed (km/h)"));

        // Weather settings
        SETTING_DEFINITIONS.put("weather.enabled",
                new SettingDefinition("geopulse.weather.enabled", "true", ValueType.BOOLEAN, "weather", "Enable weather samples for timeline stays and trips"));
        SETTING_DEFINITIONS.put("weather.primary-provider",
                new SettingDefinition("geopulse.weather.primary-provider", "OPEN_METEO", ValueType.STRING, "weather", "Primary weather provider"));
        SETTING_DEFINITIONS.put("weather.secondary-provider",
                new SettingDefinition("geopulse.weather.secondary-provider", "", ValueType.STRING, "weather", "Secondary weather provider used as fallback"));
        SETTING_DEFINITIONS.put("weather.open-meteo.enabled",
                new SettingDefinition("geopulse.weather.open-meteo.enabled", "true", ValueType.BOOLEAN, "weather", "Enable Open-Meteo weather provider"));
        SETTING_DEFINITIONS.put("weather.open-meteo.forecast-url",
                new SettingDefinition("geopulse.weather.open-meteo.forecast-url", "https://api.open-meteo.com", ValueType.STRING, "weather", "Open-Meteo forecast API base URL"));
        SETTING_DEFINITIONS.put("weather.open-meteo.archive-url",
                new SettingDefinition("geopulse.weather.open-meteo.archive-url", "https://archive-api.open-meteo.com", ValueType.STRING, "weather", "Open-Meteo archive API base URL"));
        SETTING_DEFINITIONS.put("weather.open-meteo.api-key",
                new SettingDefinition("geopulse.weather.open-meteo.api-key", "", ValueType.ENCRYPTED, "weather", "Optional Open-Meteo API key (encrypted)"));
        SETTING_DEFINITIONS.put("weather.pirate.enabled",
                new SettingDefinition("geopulse.weather.pirate.enabled", "false", ValueType.BOOLEAN, "weather", "Enable Pirate Weather provider"));
        SETTING_DEFINITIONS.put("weather.pirate.base-url",
                new SettingDefinition("geopulse.weather.pirate.base-url", "https://api.pirateweather.net", ValueType.STRING, "weather", "Pirate Weather forecast API base URL"));
        SETTING_DEFINITIONS.put("weather.pirate.time-machine-url",
                new SettingDefinition("geopulse.weather.pirate.time-machine-url", "https://timemachine.pirateweather.net", ValueType.STRING, "weather", "Pirate Weather time machine API base URL"));
        SETTING_DEFINITIONS.put("weather.pirate.api-key",
                new SettingDefinition("geopulse.weather.pirate.api-key", "", ValueType.ENCRYPTED, "weather", "Pirate Weather API key (encrypted)"));
        SETTING_DEFINITIONS.put("weather.ongoing.enabled",
                new SettingDefinition("geopulse.weather.ongoing.enabled", "true", ValueType.BOOLEAN, "weather", "Fetch weather for active latest stays/trips"));
        SETTING_DEFINITIONS.put("weather.ongoing.interval-minutes",
                new SettingDefinition("geopulse.weather.ongoing.interval-minutes", "60", ValueType.INTEGER, "weather", "Minimum minutes between ongoing weather samples"));
        SETTING_DEFINITIONS.put("weather.backfill.enabled",
                new SettingDefinition("geopulse.weather.backfill.enabled", "false", ValueType.BOOLEAN, "weather", "Enable historical weather backfill target discovery"));
        SETTING_DEFINITIONS.put("weather.quota.daily-request-limit",
                new SettingDefinition("geopulse.weather.quota.daily-request-limit", "10000", ValueType.INTEGER, "weather", "Daily provider request limit"));
        SETTING_DEFINITIONS.put("weather.quota.ongoing-reserve",
                new SettingDefinition("geopulse.weather.quota.ongoing-reserve", "500", ValueType.INTEGER, "weather", "Daily request reserve kept for ongoing weather samples"));
        SETTING_DEFINITIONS.put("weather.coordinate-precision",
                new SettingDefinition("geopulse.weather.coordinate-precision", "2", ValueType.INTEGER, "weather", "Decimal precision for weather coordinate buckets"));
        SETTING_DEFINITIONS.put("weather.failed-target-retry.enabled",
                new SettingDefinition("geopulse.weather.failed-target-retry.enabled", "true", ValueType.BOOLEAN, "weather", "Retry stale failed weather targets after cooldown"));
        SETTING_DEFINITIONS.put("weather.failed-target-retry.cooldown-hours",
                new SettingDefinition("geopulse.weather.failed-target-retry.cooldown-hours", "24", ValueType.INTEGER, "weather", "Hours before a failed weather target can be retried"));
        SETTING_DEFINITIONS.put("weather.open-meteo.connect-timeout-seconds",
                new SettingDefinition("geopulse.weather.open-meteo.connect-timeout-seconds", "5", ValueType.INTEGER, "weather", "Open-Meteo connection timeout in seconds"));
        SETTING_DEFINITIONS.put("weather.open-meteo.read-timeout-seconds",
                new SettingDefinition("geopulse.weather.open-meteo.read-timeout-seconds", "15", ValueType.INTEGER, "weather", "Open-Meteo read timeout in seconds"));
        SETTING_DEFINITIONS.put("weather.pirate.connect-timeout-seconds",
                new SettingDefinition("geopulse.weather.pirate.connect-timeout-seconds", "5", ValueType.INTEGER, "weather", "Pirate Weather connection timeout in seconds"));
        SETTING_DEFINITIONS.put("weather.pirate.read-timeout-seconds",
                new SettingDefinition("geopulse.weather.pirate.read-timeout-seconds", "15", ValueType.INTEGER, "weather", "Pirate Weather read timeout in seconds"));
        SETTING_DEFINITIONS.put("weather.targets.completed-retention-days",
                new SettingDefinition("geopulse.weather.targets.completed-retention-days", "7", ValueType.INTEGER, "weather", "Days to retain completed weather target records"));
        SETTING_DEFINITIONS.put("weather.targets.failed-retention-days",
                new SettingDefinition("geopulse.weather.targets.failed-retention-days", "30", ValueType.INTEGER, "weather", "Days to retain failed weather target records"));
        SETTING_DEFINITIONS.put("weather.targets.in-progress-timeout-minutes",
                new SettingDefinition("geopulse.weather.targets.in-progress-timeout-minutes", "60", ValueType.INTEGER, "weather", "Minutes before in-progress weather targets are considered stale"));

        // Map matching settings
        SETTING_DEFINITIONS.put("map-matching.enabled",
                new SettingDefinition("geopulse.timeline.map-matching.enabled", "false", ValueType.BOOLEAN, "map-matching", "Enable map matching globally"));
        SETTING_DEFINITIONS.put("map-matching.automatic.enabled",
                new SettingDefinition("geopulse.timeline.map-matching.automatic.enabled", "false", ValueType.BOOLEAN, "map-matching", "Automatically map-match stable new trips for all users"));
        SETTING_DEFINITIONS.put("map-matching.backfill.enabled",
                new SettingDefinition("geopulse.timeline.map-matching.backfill.enabled", "false", ValueType.BOOLEAN, "map-matching", "Discover and map-match historical trips for all users"));
        SETTING_DEFINITIONS.put("map-matching.automatic.quiet-period-minutes",
                new SettingDefinition("geopulse.timeline.map-matching.automatic.quiet-period-minutes", "15", ValueType.INTEGER, "map-matching", "Minutes a changed timeline must remain quiet before automatic matching"));
        SETTING_DEFINITIONS.put("map-matching.provider",
                new SettingDefinition("geopulse.timeline.map-matching.provider", "valhalla", ValueType.STRING, "map-matching", "Map matching provider"));
        SETTING_DEFINITIONS.put("map-matching.valhalla.base-url",
                new SettingDefinition("geopulse.timeline.map-matching.valhalla.base-url", "", ValueType.STRING, "map-matching", "Valhalla API base URL"));
        SETTING_DEFINITIONS.put("map-matching.valhalla.connect-timeout-seconds",
                new SettingDefinition("geopulse.timeline.map-matching.connect-timeout-seconds", "3", ValueType.INTEGER, "map-matching", "Valhalla connection timeout in seconds"));
        SETTING_DEFINITIONS.put("map-matching.valhalla.read-timeout-seconds",
                new SettingDefinition("geopulse.timeline.map-matching.read-timeout-seconds", "20", ValueType.INTEGER, "map-matching", "Valhalla read timeout in seconds"));
        SETTING_DEFINITIONS.put("map-matching.max-input-points",
                new SettingDefinition("geopulse.timeline.map-matching.max-input-points", "100", ValueType.INTEGER, "map-matching", "Maximum GPS points sent in each contiguous Valhalla trace chunk"));
        SETTING_DEFINITIONS.put("map-matching.max-trip-duration-hours",
                new SettingDefinition("geopulse.timeline.map-matching.max-trip-duration-hours", "24", ValueType.INTEGER, "map-matching", "Maximum trip duration eligible for map matching"));
        SETTING_DEFINITIONS.put("map-matching.worker.batch-size",
                new SettingDefinition("geopulse.timeline.map-matching.worker.batch-size", "5", ValueType.INTEGER, "map-matching", "Map matching targets processed per worker run"));
        SETTING_DEFINITIONS.put("map-matching.max-attempts",
                new SettingDefinition("geopulse.timeline.map-matching.max-attempts", "3", ValueType.INTEGER, "map-matching", "Maximum attempts per map matching target"));
        SETTING_DEFINITIONS.put("map-matching.quality.min-raw-distance-meters",
                new SettingDefinition("geopulse.timeline.map-matching.quality.min-raw-distance-meters", "500", ValueType.INTEGER, "map-matching", "Minimum raw chunk distance before matched-route quality checks apply"));
        SETTING_DEFINITIONS.put("map-matching.quality.min-distance-coverage-percent",
                new SettingDefinition("geopulse.timeline.map-matching.quality.min-distance-coverage-percent", "35", ValueType.INTEGER, "map-matching", "Minimum matched distance as a percent of raw chunk distance"));
        SETTING_DEFINITIONS.put("map-matching.quality.max-discontinuity-percent",
                new SettingDefinition("geopulse.timeline.map-matching.quality.max-discontinuity-percent", "10", ValueType.INTEGER, "map-matching", "Maximum unmatched gap distance between matched fragments as a percent of raw chunk distance"));
        SETTING_DEFINITIONS.put("map-matching.quality.max-short-discontinuity-meters",
                new SettingDefinition("geopulse.timeline.map-matching.quality.max-short-discontinuity-meters", "100", ValueType.INTEGER, "map-matching", "Minimum absolute unmatched gap allowance between matched fragments"));

        // Import settings
        SETTING_DEFINITIONS.put("import.bulk-insert-batch-size",
                new SettingDefinition("geopulse.import.bulk-insert-batch-size", "500", ValueType.INTEGER, "import", "Bulk insert batch size"));
        SETTING_DEFINITIONS.put("import.merge-batch-size",
                new SettingDefinition("geopulse.import.merge-batch-size", "250", ValueType.INTEGER, "import", "Merge batch size"));
        SETTING_DEFINITIONS.put("import.large-file-threshold-mb",
                new SettingDefinition("geopulse.import.large-file-threshold-mb", "100", ValueType.INTEGER, "import", "Large file threshold (MB)"));
        SETTING_DEFINITIONS.put("import.temp-file-retention-hours",
                new SettingDefinition("geopulse.import.temp-file-retention-hours", "24", ValueType.INTEGER, "import", "Temp file retention (hours)"));
        SETTING_DEFINITIONS.put("import.drop-folder.enabled",
                new SettingDefinition("geopulse.import.drop-folder.enabled", "false", ValueType.BOOLEAN, "import", "Enable drop folder imports (requires restart)"));
        SETTING_DEFINITIONS.put("import.drop-folder.path",
                new SettingDefinition("geopulse.import.drop-folder.path", "/data/geopulse-import", ValueType.STRING, "import", "Drop folder path (requires restart)"));
        SETTING_DEFINITIONS.put("import.drop-folder.poll-interval-seconds",
                new SettingDefinition("geopulse.import.drop-folder.poll-interval-seconds", "10", ValueType.INTEGER, "import", "Drop folder scan interval (seconds, requires restart)"));
        SETTING_DEFINITIONS.put("import.drop-folder.stable-age-seconds",
                new SettingDefinition("geopulse.import.drop-folder.stable-age-seconds", "10", ValueType.INTEGER, "import", "Min file age before import (seconds, requires restart)"));
        SETTING_DEFINITIONS.put("import.drop-folder.geopulse-max-size-mb",
                new SettingDefinition("geopulse.import.drop-folder.geopulse-max-size-mb", "200", ValueType.INTEGER, "import", "Max GeoPulse ZIP size for drop imports (MB, requires restart)"));

        // Chunked upload settings
        SETTING_DEFINITIONS.put("import.chunk-size-mb",
                new SettingDefinition("geopulse.import.chunk-size-mb", "50", ValueType.INTEGER, "import", "Size of each upload chunk in megabytes"));
        SETTING_DEFINITIONS.put("import.max-file-size-gb",
                new SettingDefinition("geopulse.import.max-file-size-gb", "10", ValueType.INTEGER, "import", "Maximum file size allowed (GB)"));
        SETTING_DEFINITIONS.put("import.upload-timeout-hours",
                new SettingDefinition("geopulse.import.upload-timeout-hours", "2", ValueType.INTEGER, "import", "Upload session timeout (hours)"));
        SETTING_DEFINITIONS.put("import.transaction-timeout-minutes",
                new SettingDefinition("geopulse.import.transaction-timeout-minutes", "1440", ValueType.INTEGER, "import", "Maximum transaction duration for one import job"));
        SETTING_DEFINITIONS.put("import.upload-cleanup-minutes",
                new SettingDefinition("geopulse.import.upload-cleanup-minutes", "15", ValueType.INTEGER, "import", "How often expired chunked upload sessions are cleaned up"));

        // GeoNames import settings
        SETTING_DEFINITIONS.put("import.geonames.cities.enabled",
                new SettingDefinition("geopulse.geonames.import.enabled", "true", ValueType.BOOLEAN, "import", "Enable GeoNames city dataset import"));
        SETTING_DEFINITIONS.put("import.geonames.cities.url",
                new SettingDefinition("geopulse.geonames.import.url", "https://download.geonames.org/export/dump/cities500.zip", ValueType.STRING, "import", "GeoNames city dataset archive URL"));
        SETTING_DEFINITIONS.put("import.geonames.cities.batch-size",
                new SettingDefinition("geopulse.geonames.import.batch-size", "1000", ValueType.INTEGER, "import", "GeoNames city import batch size"));
        SETTING_DEFINITIONS.put("import.geonames.cities.min-row-threshold",
                new SettingDefinition("geopulse.geonames.import.min-row-threshold", "100000", ValueType.INTEGER, "import", "Minimum staged GeoNames city rows required before replacing data"));
        SETTING_DEFINITIONS.put("import.geonames.cities.force-refresh",
                new SettingDefinition("geopulse.geonames.import.force-refresh", "false", ValueType.BOOLEAN, "import", "Reimport GeoNames city data even when existing data passes the threshold"));
        SETTING_DEFINITIONS.put("import.geonames.cities.connect-timeout-seconds",
                new SettingDefinition("geopulse.geonames.import.connect-timeout-seconds", "20", ValueType.INTEGER, "import", "GeoNames city download connection timeout in seconds"));
        SETTING_DEFINITIONS.put("import.geonames.cities.read-timeout-seconds",
                new SettingDefinition("geopulse.geonames.import.read-timeout-seconds", "300", ValueType.INTEGER, "import", "GeoNames city download read timeout in seconds"));
        SETTING_DEFINITIONS.put("import.geonames.countries.enabled",
                new SettingDefinition("geopulse.geonames.country-import.enabled", "true", ValueType.BOOLEAN, "import", "Enable GeoNames country dataset import"));
        SETTING_DEFINITIONS.put("import.geonames.countries.url",
                new SettingDefinition("geopulse.geonames.country-import.url", "https://download.geonames.org/export/dump/countryInfo.txt", ValueType.STRING, "import", "GeoNames country dataset URL"));
        SETTING_DEFINITIONS.put("import.geonames.countries.batch-size",
                new SettingDefinition("geopulse.geonames.country-import.batch-size", "200", ValueType.INTEGER, "import", "GeoNames country import batch size"));
        SETTING_DEFINITIONS.put("import.geonames.countries.min-row-threshold",
                new SettingDefinition("geopulse.geonames.country-import.min-row-threshold", "200", ValueType.INTEGER, "import", "Minimum staged GeoNames country rows required before replacing data"));
        SETTING_DEFINITIONS.put("import.geonames.countries.force-refresh",
                new SettingDefinition("geopulse.geonames.country-import.force-refresh", "false", ValueType.BOOLEAN, "import", "Reimport GeoNames country data even when existing data passes the threshold"));
        SETTING_DEFINITIONS.put("import.geonames.countries.connect-timeout-seconds",
                new SettingDefinition("geopulse.geonames.country-import.connect-timeout-seconds", "20", ValueType.INTEGER, "import", "GeoNames country download connection timeout in seconds"));
        SETTING_DEFINITIONS.put("import.geonames.countries.read-timeout-seconds",
                new SettingDefinition("geopulse.geonames.country-import.read-timeout-seconds", "120", ValueType.INTEGER, "import", "GeoNames country download read timeout in seconds"));

        // Streaming batch sizes for each format
        SETTING_DEFINITIONS.put("import.geojson-streaming-batch-size",
                new SettingDefinition("geopulse.import.geojson.streaming-batch-size", "500", ValueType.INTEGER, "import", "GeoJSON streaming parser batch size"));
        SETTING_DEFINITIONS.put("import.googletimeline-streaming-batch-size",
                new SettingDefinition("geopulse.import.googletimeline.streaming-batch-size", "500", ValueType.INTEGER, "import", "Google Timeline streaming parser batch size"));
        SETTING_DEFINITIONS.put("import.gpx-streaming-batch-size",
                new SettingDefinition("geopulse.import.gpx.streaming-batch-size", "500", ValueType.INTEGER, "import", "GPX streaming parser batch size"));
        SETTING_DEFINITIONS.put("import.csv-streaming-batch-size",
                new SettingDefinition("geopulse.import.csv.streaming-batch-size", "500", ValueType.INTEGER, "import", "CSV streaming parser batch size"));
        SETTING_DEFINITIONS.put("import.owntracks-streaming-batch-size",
                new SettingDefinition("geopulse.import.owntracks.streaming-batch-size", "500", ValueType.INTEGER, "import", "OwnTracks streaming parser batch size"));

        // Export settings
        SETTING_DEFINITIONS.put("export.max-jobs-per-user",
                new SettingDefinition("geopulse.export.max-jobs-per-user", "5", ValueType.INTEGER, "export", "Maximum export jobs per user"));
        SETTING_DEFINITIONS.put("export.job-expiry-hours",
                new SettingDefinition("geopulse.export.job-expiry-hours", "24", ValueType.INTEGER, "export", "Hours before export jobs expire"));
        SETTING_DEFINITIONS.put("export.concurrent-jobs-limit",
                new SettingDefinition("geopulse.export.concurrent-jobs-limit", "3", ValueType.INTEGER, "export", "Maximum concurrent export jobs to process"));
        SETTING_DEFINITIONS.put("export.batch-size",
                new SettingDefinition("geopulse.export.batch-size", "1000", ValueType.INTEGER, "export", "Default batch size for streaming exports"));
        SETTING_DEFINITIONS.put("export.trip-point-limit",
                new SettingDefinition("geopulse.export.trip-point-limit", "10000", ValueType.INTEGER, "export", "Maximum GPS points per trip export"));
        SETTING_DEFINITIONS.put("export.temp-file-retention-hours",
                new SettingDefinition("geopulse.export.temp-file-retention-hours", "24", ValueType.INTEGER, "export", "Temp file retention (hours)"));

        // Full backup settings
        SETTING_DEFINITIONS.put("backup.scheduled.enabled",
                new SettingDefinition("geopulse.backup.scheduled.enabled", "false", ValueType.BOOLEAN, "backup", "Enable scheduled full backups"));
        SETTING_DEFINITIONS.put("backup.scheduled.cron",
                new SettingDefinition("geopulse.backup.scheduled.cron", "0 0 3 * * ?", ValueType.STRING, "backup", "Cron expression for scheduled full backups"));
        SETTING_DEFINITIONS.put("backup.local.path",
                new SettingDefinition("geopulse.backup.local.path", "/data/geopulse-backups", ValueType.STRING, "backup", "Local folder path for full backup ZIP files"));
        SETTING_DEFINITIONS.put("backup.retention.count",
                new SettingDefinition("geopulse.backup.retention.count", "7", ValueType.INTEGER, "backup", "Number of local full backups to retain"));
        SETTING_DEFINITIONS.put("backup.operation.timeout-minutes",
                new SettingDefinition("geopulse.backup.operation.timeout-minutes", "120", ValueType.INTEGER, "backup", "Maximum duration for full backup and restore operations"));

        // System performance
        SETTING_DEFINITIONS.put(DEFAULT_DISTANCE_UNIT_KEY,
                new SettingDefinition("geopulse.user.default-distance-unit", "KILOMETERS", ValueType.STRING, "system", "Default distance unit for newly created users"));
        SETTING_DEFINITIONS.put(DEFAULT_TEMPERATURE_UNIT_KEY,
                new SettingDefinition("geopulse.user.default-temperature-unit", "CELSIUS", ValueType.STRING, "system", "Default temperature unit for newly created users"));
        SETTING_DEFINITIONS.put("system.timeline.processing.thread-pool-size",
                new SettingDefinition("geopulse.timeline.processing.thread-pool-size", "2", ValueType.INTEGER, "system", "Timeline processing threads"));
        SETTING_DEFINITIONS.put("system.timeline.view.item-limit",
                new SettingDefinition("geopulse.timeline.view.item-limit", "150", ValueType.INTEGER, "system", "Max timeline items in view"));
        SETTING_DEFINITIONS.put("system.version-check.github-api-url",
                new SettingDefinition("geopulse.version-check.github-api-url", "https://api.github.com/repos/tess1o/geopulse/releases/latest", ValueType.STRING, "system", "GitHub API URL used to check for GeoPulse updates"));
        SETTING_DEFINITIONS.put("system.version-check.release-url",
                new SettingDefinition("geopulse.version-check.release-url", "https://github.com/tess1o/geopulse/releases", ValueType.STRING, "system", "Fallback release page URL for update notifications"));
        SETTING_DEFINITIONS.put("system.version-check.cache-ttl-minutes",
                new SettingDefinition("geopulse.version-check.cache-ttl-minutes", "60", ValueType.INTEGER, "system", "Minutes to cache version update checks"));
        SETTING_DEFINITIONS.put("system.version-check.connect-timeout-seconds",
                new SettingDefinition("geopulse.version-check.connect-timeout-seconds", "5", ValueType.INTEGER, "system", "Version check connection timeout in seconds"));
        SETTING_DEFINITIONS.put("system.version-check.read-timeout-seconds",
                new SettingDefinition("geopulse.version-check.read-timeout-seconds", "8", ValueType.INTEGER, "system", "Version check read timeout in seconds"));
        SETTING_DEFINITIONS.put("system.water-dataset.url",
                new SettingDefinition("geopulse.water-dataset.url", "https://github.com/tess1o/GeoPulse/releases/download/water-surfaces-v1/geopulse-water-surfaces-v1.copy.gz", ValueType.STRING, "system", "Water dataset archive URL used for Boat setup"));
        SETTING_DEFINITIONS.put("system.water-dataset.sha256",
                new SettingDefinition("geopulse.water-dataset.sha256", "", ValueType.STRING, "system", "Expected SHA-256 checksum for the water dataset archive"));
        SETTING_DEFINITIONS.put("system.water-dataset.auto-import",
                new SettingDefinition("geopulse.water-dataset.auto-import", "true", ValueType.BOOLEAN, "system", "Automatically import the Boat water dataset when needed"));
        SETTING_DEFINITIONS.put("system.water-dataset.connect-timeout-seconds",
                new SettingDefinition("geopulse.water-dataset.connect-timeout-seconds", "30", ValueType.INTEGER, "system", "Water dataset download connection timeout in seconds"));
        SETTING_DEFINITIONS.put("system.water-dataset.download-timeout-hours",
                new SettingDefinition("geopulse.water-dataset.download-timeout-hours", "6", ValueType.INTEGER, "system", "Maximum duration for a water dataset download"));
        SETTING_DEFINITIONS.put("system.water-dataset.download-stall-timeout-seconds",
                new SettingDefinition("geopulse.water-dataset.download-stall-timeout-seconds", "120", ValueType.INTEGER, "system", "Seconds without downloaded bytes before the water dataset download is treated as stalled"));
        SETTING_DEFINITIONS.put("system.water-dataset.setup-start-timeout-minutes",
                new SettingDefinition("geopulse.water-dataset.setup-start-timeout-minutes", "5", ValueType.INTEGER, "system", "Minutes before a queued Boat setup job is considered failed to start"));
        SETTING_DEFINITIONS.put("system.notifications.apprise.enabled",
                new SettingDefinition("geopulse.notifications.apprise.enabled", "false", ValueType.BOOLEAN, "system", "Enable/disable Apprise external notifications"));
        SETTING_DEFINITIONS.put("system.notifications.apprise.api-url",
                new SettingDefinition("geopulse.notifications.apprise.api-url", "", ValueType.STRING, "system", "Apprise API base URL (for example http://apprise-api:8000)"));
        SETTING_DEFINITIONS.put("system.notifications.apprise.auth-token",
                new SettingDefinition("geopulse.notifications.apprise.auth-token", "", ValueType.ENCRYPTED, "system", "Optional Apprise API key/token"));
        SETTING_DEFINITIONS.put("system.notifications.apprise.timeout-ms",
                new SettingDefinition("geopulse.notifications.apprise.timeout-ms", "5000", ValueType.INTEGER, "system", "Apprise HTTP timeout in milliseconds"));
        SETTING_DEFINITIONS.put("system.notifications.apprise.verify-tls",
                new SettingDefinition("geopulse.notifications.apprise.verify-tls", "true", ValueType.BOOLEAN, "system", "Verify TLS certificates when connecting to Apprise"));
        SETTING_DEFINITIONS.put("system.notifications.geofence-events.cleanup.enabled",
                new SettingDefinition("geopulse.notifications.geofence-events.cleanup.enabled", "true", ValueType.BOOLEAN, "system", "Enable scheduled cleanup of old geofence notification events"));
        SETTING_DEFINITIONS.put("system.notifications.geofence-events.retention-days",
                new SettingDefinition("geopulse.notifications.geofence-events.retention-days", "90", ValueType.INTEGER, "system", "Delete geofence events older than N days"));
        SETTING_DEFINITIONS.put("system.notifications.user-notifications.cleanup.enabled",
                new SettingDefinition("geopulse.notifications.user-notifications.cleanup.enabled", "true", ValueType.BOOLEAN, "system", "Enable scheduled cleanup of old user inbox notifications"));
        SETTING_DEFINITIONS.put("system.notifications.user-notifications.retention-days",
                new SettingDefinition("geopulse.notifications.user-notifications.retention-days", "90", ValueType.INTEGER, "system", "Delete user inbox notifications older than N days"));

        // AI Assistant settings
        SETTING_DEFINITIONS.put("ai.default-system-message",
                new SettingDefinition("geopulse.ai.default-system-message", "", ValueType.STRING, "ai", "Global default system message for AI assistant (empty = use built-in default)"));
        SETTING_DEFINITIONS.put("ai.logging.enabled",
                new SettingDefinition("geopulse.ai.logging.enabled", "false", ValueType.BOOLEAN, "ai", "Enable detailed AI request/response logging for debugging"));
        SETTING_DEFINITIONS.put("ai.chat-memory.max-messages",
                new SettingDefinition("geopulse.ai.chat-memory.max-messages", "10", ValueType.INTEGER, "ai", "Maximum number of messages to keep in conversation history per user"));
        SETTING_DEFINITIONS.put("ai.tool-result.max-length",
                new SettingDefinition("geopulse.ai.tool-result.max-length", "12000", ValueType.INTEGER, "ai", "Maximum characters in tool results (prevents token limit errors)"));
    }

    @Inject
    public SystemSettingsService(
            SystemSettingsRepository repository,
            AIEncryptionService encryptionService,
            Event<WeatherSettingsChangedEvent> weatherSettingsChangedEvent,
            Event<MapMatchingSettingsChangedEvent> mapMatchingSettingsChangedEvent) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.weatherSettingsChangedEvent = weatherSettingsChangedEvent;
        this.mapMatchingSettingsChangedEvent = mapMatchingSettingsChangedEvent;
        this.config = ConfigProvider.getConfig();
    }

    // Retained for focused unit tests and non-CDI callers that predate the
    // map-matching settings event. CDI always uses the @Inject constructor.
    public SystemSettingsService(
            SystemSettingsRepository repository,
            AIEncryptionService encryptionService,
            Event<WeatherSettingsChangedEvent> weatherSettingsChangedEvent) {
        this(repository, encryptionService, weatherSettingsChangedEvent, null);
    }

    /**
     * Get a setting value. Checks DB first, falls back to env var.
     */
    public String getString(String key) {
        return getValue(key);
    }

    public boolean getBoolean(String key) {
        String value = getValue(key);
        return Boolean.parseBoolean(value);
    }

    public int getInteger(String key) {
        String value = getValue(key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value for setting {}: {}", key, value);
            SettingDefinition def = SETTING_DEFINITIONS.get(key);
            return def != null ? Integer.parseInt(def.defaultValue()) : 0;
        }
    }

    public DistanceUnit getDefaultDistanceUnit() {
        return parseDistanceUnitOrDefault(getValue(DEFAULT_DISTANCE_UNIT_KEY));
    }

    public TemperatureUnit getDefaultTemperatureUnit() {
        return parseTemperatureUnitOrDefault(getValue(DEFAULT_TEMPERATURE_UNIT_KEY));
    }

    public Map<String, SettingDefinition> getSettingDefinitions() {
        return Collections.unmodifiableMap(SETTING_DEFINITIONS);
    }

    public void validateValueForImport(String key, String value) {
        SettingDefinition def = SETTING_DEFINITIONS.get(key);
        if (def == null) {
            throw new IllegalArgumentException("Unknown setting key: " + key);
        }
        validateValue(value, def.valueType());
        validateSettingConstraints(key, value);
    }

    /**
     * Get value with DB-first, env-fallback pattern.
     */
    private String getValue(String key) {
        // Check DB first
        Optional<SystemSettingsEntity> dbSetting = repository.findByKey(key);
        if (dbSetting.isPresent()) {
            SystemSettingsEntity entity = dbSetting.get();

            // Decrypt if type is ENCRYPTED
            if (entity.getValueType() == ValueType.ENCRYPTED) {
                try {
                    String decrypted = encryptionService.decrypt(
                            entity.getValue(),
                            entity.getEncryptionKeyId()
                    );
                    log.trace("Using decrypted DB value for setting: {}", key);
                    return decrypted;
                } catch (Exception e) {
                    log.error("Failed to decrypt setting {}: {}", key, e.getMessage());
                    throw new RuntimeException("Decryption failed for setting: " + key, e);
                }
            }

            log.trace("Using DB value for setting: {}", key);
            return entity.getValue();
        }

        // Fall back to env var
        SettingDefinition def = SETTING_DEFINITIONS.get(key);
        if (def != null) {
            String envValue = config.getOptionalValue(def.envVarName(), String.class)
                    .orElse(def.defaultValue());
            envValue = normalizeConfigFallbackValue(envValue);
            if (DEFAULT_DISTANCE_UNIT_KEY.equals(key)) {
                envValue = parseDistanceUnitOrDefault(envValue).name();
            }
            if (DEFAULT_TEMPERATURE_UNIT_KEY.equals(key)) {
                envValue = parseTemperatureUnitOrDefault(envValue).name();
            }
            log.trace("Using env/default value for setting {}: {}", key, envValue);
            return envValue;
        }

        log.warn("Unknown setting key: {}", key);
        return "";
    }

    /**
     * Get the default value (from env var or hardcoded default).
     */
    public String getDefaultValue(String key) {
        SettingDefinition def = SETTING_DEFINITIONS.get(key);
        if (def != null) {
            String defaultValue = config.getOptionalValue(def.envVarName(), String.class)
                    .orElse(def.defaultValue());
            defaultValue = normalizeConfigFallbackValue(defaultValue);
            if (DEFAULT_DISTANCE_UNIT_KEY.equals(key)) {
                return parseDistanceUnitOrDefault(defaultValue).name();
            }
            if (DEFAULT_TEMPERATURE_UNIT_KEY.equals(key)) {
                return parseTemperatureUnitOrDefault(defaultValue).name();
            }
            return defaultValue;
        }
        return "";
    }

    private String normalizeConfigFallbackValue(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    /**
     * Check if a setting is using the default value (not overridden in DB).
     */
    public boolean isDefault(String key) {
        return repository.findByKey(key).isEmpty();
    }

    /**
     * Set a setting value in DB.
     */
    @Transactional
    public void setValue(String key, String value, UUID updatedBy) {
        SettingDefinition def = SETTING_DEFINITIONS.get(key);
        if (def == null) {
            throw new IllegalArgumentException("Unknown setting key: " + key);
        }

        // Validate value type
        validateValue(value, def.valueType());
        validateSettingConstraints(key, value);
        if (DEFAULT_DISTANCE_UNIT_KEY.equals(key)) {
            value = parseDistanceUnit(value).name();
        }
        if (DEFAULT_TEMPERATURE_UNIT_KEY.equals(key)) {
            value = parseTemperatureUnit(value).name();
        }

        // Encrypt if type is ENCRYPTED
        String storedValue = value;
        String keyId = null;
        if (def.valueType() == ValueType.ENCRYPTED) {
            storedValue = encryptionService.encrypt(value);
            keyId = encryptionService.getCurrentKeyId();
        }

        Optional<SystemSettingsEntity> existing = repository.findByKey(key);
        if (existing.isPresent()) {
            SystemSettingsEntity entity = existing.get();
            entity.setValue(storedValue);
            entity.setUpdatedBy(updatedBy);
            entity.setUpdatedAt(Instant.now());
            entity.setEncryptionKeyId(keyId);
        } else {
            SystemSettingsEntity entity = SystemSettingsEntity.builder()
                    .key(key)
                    .value(storedValue)
                    .valueType(def.valueType())
                    .category(def.category())
                    .description(def.description())
                    .updatedBy(updatedBy)
                    .updatedAt(Instant.now())
                    .encryptionKeyId(keyId)
                    .build();
            repository.persist(entity);
        }

        log.info("Setting {} updated by user {}", key, updatedBy);
        fireWeatherSettingsChanged(key);
        fireMapMatchingSettingsChanged(key);
    }

    /**
     * Reset a setting to default (delete from DB).
     */
    @Transactional
    public void resetToDefault(String key) {
        repository.deleteByKey(key);
        log.info("Setting {} reset to default", key);
        fireWeatherSettingsChanged(key);
        fireMapMatchingSettingsChanged(key);
    }

    private void fireWeatherSettingsChanged(String key) {
        if (key != null && key.startsWith("weather.")) {
            weatherSettingsChangedEvent.fire(new WeatherSettingsChangedEvent(key));
        }
    }

    private void fireMapMatchingSettingsChanged(String key) {
        if (key != null && key.startsWith("map-matching.")) {
            if (mapMatchingSettingsChangedEvent != null) {
                mapMatchingSettingsChangedEvent.fire(new MapMatchingSettingsChangedEvent(key));
            }
        }
    }

    /**
     * Get all settings for a category.
     */
    public List<SettingInfo> getSettingsByCategory(String category) {
        List<SettingInfo> result = new ArrayList<>();

        for (Map.Entry<String, SettingDefinition> entry : SETTING_DEFINITIONS.entrySet()) {
            String key = entry.getKey();
            SettingDefinition def = entry.getValue();

            if (def.category().equals(category)) {
                String currentValue = getValue(key);
                String defaultValue = getDefaultValue(key);
                boolean isDefault = isDefault(key);

                // Mask encrypted values in API responses
                String displayValue = currentValue;
                String displayDefault = defaultValue;

                if (def.valueType() == ValueType.ENCRYPTED) {
                    displayValue = currentValue.isEmpty() ? "" : "********";
                    displayDefault = defaultValue.isEmpty() ? "" : "********";
                }

                result.add(new SettingInfo(
                        key,
                        displayValue,
                        def.valueType(),
                        def.category(),
                        def.description(),
                        isDefault,
                        displayDefault
                ));
            }
        }

        if ("import".equals(category)) {
            String identity = ProcessIdentity.describe();
            result.add(new SettingInfo(
                    IMPORT_DROP_FOLDER_IDENTITY_KEY,
                    identity,
                    ValueType.STRING,
                    "import",
                    "Effective user/group running GeoPulse (read-only)",
                    true,
                    identity
            ));
        }

        return result;
    }

    /**
     * Get all settings grouped by category.
     */
    public Map<String, List<SettingInfo>> getAllSettings() {
        Map<String, List<SettingInfo>> result = new LinkedHashMap<>();

        for (String category : List.of("auth", "geocoding", "weather", "map-matching", "ai", "gps", "import", "export", "system")) {
            result.put(category, getSettingsByCategory(category));
        }

        return result;
    }

    private void validateValue(String value, ValueType type) {
        switch (type) {
            case BOOLEAN:
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException("Invalid boolean value: " + value);
                }
                break;
            case INTEGER:
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid integer value: " + value);
                }
                break;
            case STRING:
            case ENCRYPTED:
                // Any string is valid
                break;
        }
    }

    private void validateSettingConstraints(String key, String value) {
        if ("system.notifications.geofence-events.retention-days".equals(key)) {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new IllegalArgumentException("Setting " + key + " must be at least 1 day");
            }
        }
        if ("system.notifications.user-notifications.retention-days".equals(key)) {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new IllegalArgumentException("Setting " + key + " must be at least 1 day");
            }
        }
        if (DEFAULT_DISTANCE_UNIT_KEY.equals(key)) {
            parseDistanceUnit(value);
        }
        if (DEFAULT_TEMPERATURE_UNIT_KEY.equals(key)) {
            parseTemperatureUnit(value);
        }
        if ("weather.ongoing.interval-minutes".equals(key)) {
            int parsed = Integer.parseInt(value);
            if (parsed < 30) {
                throw new IllegalArgumentException("Setting " + key + " must be at least 30 minutes");
            }
        }
        if ("weather.quota.daily-request-limit".equals(key)) {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new IllegalArgumentException("Setting " + key + " must be zero or greater");
            }
        }
        if ("weather.quota.ongoing-reserve".equals(key)) {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new IllegalArgumentException("Setting " + key + " must be zero or greater");
            }
        }
        if ("weather.coordinate-precision".equals(key)) {
            int parsed = Integer.parseInt(value);
            if (parsed < 0 || parsed > 5) {
                throw new IllegalArgumentException("Setting " + key + " must be between 0 and 5");
            }
        }
        if (key.startsWith("weather.") && SETTING_DEFINITIONS.get(key).valueType() == ValueType.INTEGER
                && !"weather.quota.daily-request-limit".equals(key)
                && !"weather.quota.ongoing-reserve".equals(key)
                && !"weather.coordinate-precision".equals(key)) {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new IllegalArgumentException("Setting " + key + " must be at least 1");
            }
        }
        if (key.startsWith("weather.") && (key.endsWith("-url") || key.endsWith(".url"))) {
            validateOptionalHttpUrl(key, value);
        }
        if ("weather.primary-provider".equals(key) || "weather.secondary-provider".equals(key)) {
            String normalized = value == null ? "" : value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
            if (!normalized.isBlank()
                    && !normalized.equals("OPEN_METEO")
                    && !normalized.equals("PIRATE_WEATHER")) {
                throw new IllegalArgumentException("Setting " + key + " must be OPEN_METEO, PIRATE_WEATHER, or empty");
            }
        }
        if ("map-matching.provider".equals(key)) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if (!"valhalla".equals(normalized)) {
                throw new IllegalArgumentException("Setting " + key + " must be valhalla");
            }
        }
        if ("map-matching.valhalla.base-url".equals(key)) {
            String normalized = value == null ? "" : value.trim();
            if (!normalized.isBlank()
                    && !normalized.startsWith("http://")
                    && !normalized.startsWith("https://")) {
                throw new IllegalArgumentException("Setting " + key + " must start with http:// or https://");
            }
        }
        if (key.startsWith("map-matching.") && SETTING_DEFINITIONS.get(key).valueType() == ValueType.INTEGER) {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new IllegalArgumentException("Setting " + key + " must be at least 1");
            }
        }
        if ("map-matching.quality.min-distance-coverage-percent".equals(key)
                || "map-matching.quality.max-discontinuity-percent".equals(key)) {
            int parsed = Integer.parseInt(value);
            if (parsed > 100) {
                throw new IllegalArgumentException("Setting " + key + " must be at most 100");
            }
        }
        if (key.startsWith("auth.oidc.") && key.endsWith("-url")) {
            validateOptionalHttpUrl(key, value);
        }
        if ("auth.oidc.jwks-cache.ttl-hours".equals(key)) {
            requireIntegerAtLeast(key, value, 1);
        }
        if (key.startsWith("geocoding.reconcile.") || "geocoding.cache.max-bbox-area-km2".equals(key)) {
            requireIntegerAtLeast(key, value, 1);
        }
        if (key.startsWith("import.geonames.") && key.endsWith(".url")) {
            validateRequiredHttpUrl(key, value);
        }
        if (key.startsWith("import.geonames.") && SETTING_DEFINITIONS.get(key).valueType() == ValueType.INTEGER) {
            requireIntegerAtLeast(key, value, 1);
        }
        if ("import.transaction-timeout-minutes".equals(key) || "import.upload-cleanup-minutes".equals(key)) {
            requireIntegerAtLeast(key, value, 1);
        }
        if (key.startsWith("system.version-check.") && key.endsWith("-url")) {
            validateRequiredHttpUrl(key, value);
        }
        if (key.startsWith("system.version-check.") && SETTING_DEFINITIONS.get(key).valueType() == ValueType.INTEGER) {
            requireIntegerAtLeast(key, value, 1);
        }
        if ("system.water-dataset.url".equals(key)) {
            validateRequiredHttpUrl(key, value);
        }
        if ("system.water-dataset.sha256".equals(key)) {
            String trimmed = value == null ? "" : value.trim();
            if (!trimmed.isBlank() && !trimmed.matches("^[a-fA-F0-9]{64}$")) {
                throw new IllegalArgumentException("Setting " + key + " must be a SHA-256 hex digest");
            }
        }
        if (key.startsWith("system.water-dataset.") && SETTING_DEFINITIONS.get(key).valueType() == ValueType.INTEGER) {
            requireIntegerAtLeast(key, value, 1);
        }
    }

    private void requireIntegerAtLeast(String key, String value, int minimum) {
        int parsed = Integer.parseInt(value);
        if (parsed < minimum) {
            throw new IllegalArgumentException("Setting " + key + " must be at least " + minimum);
        }
    }

    private void validateOptionalHttpUrl(String key, String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.isBlank()) {
            validateRequiredHttpUrl(key, trimmed);
        }
    }

    private void validateRequiredHttpUrl(String key, String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank()
                || (!trimmed.startsWith("http://") && !trimmed.startsWith("https://"))) {
            throw new IllegalArgumentException("Setting " + key + " must start with http:// or https://");
        }
    }

    private DistanceUnit parseDistanceUnitOrDefault(String value) {
        try {
            return parseDistanceUnit(value);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid default distance unit '{}'. Falling back to KILOMETERS", value);
            return DistanceUnit.KILOMETERS;
        }
    }

    private DistanceUnit parseDistanceUnit(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Setting " + DEFAULT_DISTANCE_UNIT_KEY + " must be KILOMETERS or MILES");
        }

        try {
            return DistanceUnit.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Setting " + DEFAULT_DISTANCE_UNIT_KEY + " must be KILOMETERS or MILES");
        }
    }

    private TemperatureUnit parseTemperatureUnitOrDefault(String value) {
        try {
            return parseTemperatureUnit(value);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid default temperature unit '{}'. Falling back to CELSIUS", value);
            return TemperatureUnit.CELSIUS;
        }
    }

    private TemperatureUnit parseTemperatureUnit(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Setting " + DEFAULT_TEMPERATURE_UNIT_KEY + " must be CELSIUS or FAHRENHEIT");
        }

        try {
            return TemperatureUnit.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Setting " + DEFAULT_TEMPERATURE_UNIT_KEY + " must be CELSIUS or FAHRENHEIT");
        }
    }
}
