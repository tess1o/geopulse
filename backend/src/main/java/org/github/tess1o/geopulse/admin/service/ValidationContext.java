package org.github.tess1o.geopulse.admin.service;

import java.util.Map;

/**
 * Provides context-aware access to setting values during validation.
 * <p>
 * When validating bulk setting updates, individual settings may depend on other settings
 * that are being changed in the same request. This class resolves values by checking
 * pending changes first, falling back to persisted database state.
 * <p>
 * Example use case: Enabling Google Maps requires both an API key and the enabled flag.
 * When both are changed together:
 * <pre>
 * [
 *   {"key": "geocoding.googlemaps.api-key", "value": "xyz123"},
 *   {"key": "geocoding.googlemaps.enabled", "value": "true"}
 * ]
 * </pre>
 * Validating the enabled flag needs to see the pending API key, not the old DB value.
 */
public class ValidationContext {
    private final Map<String, String> pendingChanges;
    private final SystemSettingsService settingsService;

    /**
     * Creates a validation context.
     *
     * @param pendingChanges Map of setting keys to new values being applied
     * @param settingsService Service for reading current persisted values
     */
    public ValidationContext(
            Map<String, String> pendingChanges,
            SystemSettingsService settingsService
    ) {
        this.pendingChanges = pendingChanges;
        this.settingsService = settingsService;
    }

    /**
     * Gets the effective value for a setting, considering pending changes.
     * <p>
     * Returns the pending value if present in current batch, otherwise
     * returns the persisted value from the database.
     *
     * @param key Setting key (e.g., "geocoding.googlemaps.api-key")
     * @return The effective value, or null if not set anywhere
     */
    public String getValue(String key) {
        if (pendingChanges.containsKey(key)) {
            return pendingChanges.get(key);
        }
        return settingsService.getString(key);
    }

    /**
     * Gets the effective boolean value for a setting.
     *
     * @param key Setting key
     * @return true if value is "true" (case-insensitive), false otherwise
     */
    public boolean getBoolean(String key) {
        String value = getValue(key);
        return value != null && Boolean.parseBoolean(value);
    }

    /**
     * Checks if a setting has a non-empty value (pending or persisted).
     * <p>
     * Masked values (e.g., "********") are considered empty.
     *
     * @param key Setting key
     * @return true if value exists and is not blank or masked
     */
    public boolean hasValue(String key) {
        String value = getValue(key);
        return value != null && !value.isBlank() && !value.equals("********");
    }
}
