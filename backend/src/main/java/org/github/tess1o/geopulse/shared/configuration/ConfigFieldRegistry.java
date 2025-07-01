package org.github.tess1o.geopulse.shared.configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry for configuration fields that can be used to automatically handle
 * configuration merging, property loading, and field updates without duplication.
 *
 * @param <T> The configuration type (e.g., TimelineConfig)
 */
public class ConfigFieldRegistry<T> {
    private final List<ConfigField<T, ?>> fields = new ArrayList<>();

    public <V> ConfigFieldRegistry<T> register(ConfigField<T, V> field) {
        fields.add(field);
        return this;
    }

    public List<ConfigField<T, ?>> getFields() {
        return new ArrayList<>(fields);
    }

    /**
     * Merge user preferences into base configuration.
     * Only overwrites base values where user preferences are non-null.
     */
    @SuppressWarnings("unchecked")
    public void mergeUserPreferences(T baseConfig, T userPreferences) {
        for (ConfigField<T, ?> field : fields) {
            Object userValue = field.getValue(userPreferences);
            if (userValue != null) {
                ((ConfigField<T, Object>) field).setValue(baseConfig, userValue);
            }
        }
    }

    /**
     * Update configuration with provided values.
     * Only updates fields where new values are non-null.
     */
    @SuppressWarnings("unchecked")
    public void updateConfiguration(T config, T updates) {
        for (ConfigField<T, ?> field : fields) {
            Object updateValue = field.getValue(updates);
            if (updateValue != null) {
                ((ConfigField<T, Object>) field).setValue(config, updateValue);
            }
        }
    }
}