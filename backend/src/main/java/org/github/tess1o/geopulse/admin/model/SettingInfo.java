package org.github.tess1o.geopulse.admin.model;

/**
 * Full information about a setting for API responses.
 */
public record SettingInfo(
        String key,
        String value,
        ValueType valueType,
        String category,
        String description,
        boolean isDefault,
        String defaultValue
) {}
