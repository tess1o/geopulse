package org.github.tess1o.geopulse.shared.configuration;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Utility for loading configuration from application.properties using field registry.
 */
public class PropertyBasedConfigLoader {

    /**
     * Load configuration from application.properties using the field registry.
     */
    public static <T> T loadFromProperties(ConfigFieldRegistry<T> registry, T configInstance) {
        Config config = ConfigProvider.getConfig();
        
        for (ConfigField<T, ?> field : registry.getFields()) {
            String propertyValue = config.getOptionalValue(field.propertyName(), String.class)
                    .orElse(field.defaultValue());
            
            Object parsedValue = field.parseValue(propertyValue);
            setFieldValue(field, configInstance, parsedValue);
        }
        
        return configInstance;
    }

    @SuppressWarnings("unchecked")
    private static <T> void setFieldValue(ConfigField<T, ?> field, T config, Object value) {
        ((ConfigField<T, Object>) field).setValue(config, value);
    }
}