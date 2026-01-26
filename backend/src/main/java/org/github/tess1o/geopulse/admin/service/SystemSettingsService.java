package org.github.tess1o.geopulse.admin.service;

import jakarta.enterprise.context.ApplicationScoped;
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
        SETTING_DEFINITIONS.put("auth.login.enabled",
                new SettingDefinition("geopulse.auth.login.enabled", "true", ValueType.BOOLEAN, "auth", "Enable/disable all login"));
        SETTING_DEFINITIONS.put("auth.password-login.enabled",
                new SettingDefinition("geopulse.auth.password-login.enabled", "true", ValueType.BOOLEAN, "auth", "Enable/disable password login"));
        SETTING_DEFINITIONS.put("auth.oidc.login.enabled",
                new SettingDefinition("geopulse.auth.oidc.login.enabled", "true", ValueType.BOOLEAN, "auth", "Enable/disable OIDC login"));
        SETTING_DEFINITIONS.put("auth.admin-login-bypass.enabled",
                new SettingDefinition("geopulse.auth.admin-login-bypass.enabled", "true", ValueType.BOOLEAN, "auth", "Allow admins to bypass login restrictions"));

        // Geocoding settings - General
        SETTING_DEFINITIONS.put("geocoding.primary-provider",
                new SettingDefinition("geocoding.provider.primary", "nominatim", ValueType.STRING, "geocoding", "Primary geocoding provider"));
        SETTING_DEFINITIONS.put("geocoding.fallback-provider",
                new SettingDefinition("geocoding.provider.fallback", "", ValueType.STRING, "geocoding", "Fallback geocoding provider (optional)"));
        SETTING_DEFINITIONS.put("geocoding.delay-ms",
                new SettingDefinition("geocoding.provider.delay.ms", "1000", ValueType.INTEGER, "geocoding", "Delay between geocoding requests (milliseconds)"));

        // Geocoding settings - Provider Availability
        SETTING_DEFINITIONS.put("geocoding.nominatim.enabled",
                new SettingDefinition("geocoding.provider.nominatim.enabled", "true", ValueType.BOOLEAN, "geocoding", "Enable Nominatim geocoding provider"));
        SETTING_DEFINITIONS.put("geocoding.nominatim.url",
                new SettingDefinition("quarkus.rest-client.nominatim-api.url", "", ValueType.STRING, "geocoding", "Custom Nominatim server URL (optional)"));
        SETTING_DEFINITIONS.put("geocoding.nominatim.language",
                new SettingDefinition("geocoding.nominatim.language", "", ValueType.STRING, "geocoding", "Nominatim language preference (BCP 47: en-US, de, uk, ja)"));

        SETTING_DEFINITIONS.put("geocoding.photon.enabled",
                new SettingDefinition("geocoding.provider.photon.enabled", "false", ValueType.BOOLEAN, "geocoding", "Enable Photon geocoding provider"));
        SETTING_DEFINITIONS.put("geocoding.photon.url",
                new SettingDefinition("quarkus.rest-client.photon-api.url", "", ValueType.STRING, "geocoding", "Custom Photon server URL (optional)"));
        SETTING_DEFINITIONS.put("geocoding.photon.language",
                new SettingDefinition("geocoding.photon.language", "", ValueType.STRING, "geocoding", "Photon language preference (BCP 47: en-US, de, uk, ja)"));

        SETTING_DEFINITIONS.put("geocoding.googlemaps.enabled",
                new SettingDefinition("geocoding.provider.googlemaps.enabled", "false", ValueType.BOOLEAN, "geocoding", "Enable Google Maps geocoding provider"));
        SETTING_DEFINITIONS.put("geocoding.googlemaps.api-key",
                new SettingDefinition("geocoding.provider.googlemaps.api-key", "", ValueType.ENCRYPTED, "geocoding", "Google Maps API key (encrypted)"));

        SETTING_DEFINITIONS.put("geocoding.mapbox.enabled",
                new SettingDefinition("geocoding.provider.mapbox.enabled", "false", ValueType.BOOLEAN, "geocoding", "Enable Mapbox geocoding provider"));
        SETTING_DEFINITIONS.put("geocoding.mapbox.access-token",
                new SettingDefinition("geocoding.provider.mapbox.access-token", "", ValueType.ENCRYPTED, "geocoding", "Mapbox access token (encrypted)"));

        // GPS processing defaults
        SETTING_DEFINITIONS.put("gps.filter.inaccurate-data.enabled",
                new SettingDefinition("geopulse.gps.filter.inaccurate-data.enabled", "false", ValueType.BOOLEAN, "gps", "Default: filter inaccurate GPS data"));
        SETTING_DEFINITIONS.put("gps.max-allowed-accuracy",
                new SettingDefinition("geopulse.gps.max-allowed-accuracy", "100", ValueType.INTEGER, "gps", "Max allowed accuracy (meters)"));
        SETTING_DEFINITIONS.put("gps.max-allowed-speed",
                new SettingDefinition("geopulse.gps.max-allowed-speed", "250", ValueType.INTEGER, "gps", "Max allowed speed (km/h)"));

        // Import settings
        SETTING_DEFINITIONS.put("import.bulk-insert-batch-size",
                new SettingDefinition("geopulse.import.bulk-insert-batch-size", "500", ValueType.INTEGER, "import", "Bulk insert batch size"));
        SETTING_DEFINITIONS.put("import.merge-batch-size",
                new SettingDefinition("geopulse.import.merge-batch-size", "250", ValueType.INTEGER, "import", "Merge batch size"));
        SETTING_DEFINITIONS.put("import.large-file-threshold-mb",
                new SettingDefinition("geopulse.import.large-file-threshold-mb", "100", ValueType.INTEGER, "import", "Large file threshold (MB)"));
        SETTING_DEFINITIONS.put("import.temp-file-retention-hours",
                new SettingDefinition("geopulse.import.temp-file-retention-hours", "24", ValueType.INTEGER, "import", "Temp file retention (hours)"));

        // Chunked upload settings
        SETTING_DEFINITIONS.put("import.chunk-size-mb",
                new SettingDefinition("geopulse.import.chunk-size-mb", "50", ValueType.INTEGER, "import", "Size of each upload chunk in megabytes"));
        SETTING_DEFINITIONS.put("import.max-file-size-gb",
                new SettingDefinition("geopulse.import.max-file-size-gb", "10", ValueType.INTEGER, "import", "Maximum file size allowed (GB)"));
        SETTING_DEFINITIONS.put("import.upload-timeout-hours",
                new SettingDefinition("geopulse.import.upload-timeout-hours", "2", ValueType.INTEGER, "import", "Upload session timeout (hours)"));

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

        // System performance
        SETTING_DEFINITIONS.put("system.timeline.processing.thread-pool-size",
                new SettingDefinition("geopulse.timeline.processing.thread-pool-size", "2", ValueType.INTEGER, "system", "Timeline processing threads"));
        SETTING_DEFINITIONS.put("system.timeline.view.item-limit",
                new SettingDefinition("geopulse.timeline.view.item-limit", "150", ValueType.INTEGER, "system", "Max timeline items in view"));

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
            AIEncryptionService encryptionService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.config = ConfigProvider.getConfig();
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
            return config.getOptionalValue(def.envVarName(), String.class)
                    .orElse(def.defaultValue());
        }
        return "";
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
    }

    /**
     * Reset a setting to default (delete from DB).
     */
    @Transactional
    public void resetToDefault(String key) {
        repository.deleteByKey(key);
        log.info("Setting {} reset to default", key);
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

        return result;
    }

    /**
     * Get all settings grouped by category.
     */
    public Map<String, List<SettingInfo>> getAllSettings() {
        Map<String, List<SettingInfo>> result = new LinkedHashMap<>();

        for (String category : List.of("auth", "geocoding", "gps", "import", "export", "system")) {
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
}
