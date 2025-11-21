package org.github.tess1o.geopulse.admin.model;

/**
 * Definition of a setting including its env var mapping.
 */
public record SettingDefinition(
        String envVarName,
        String defaultValue,
        ValueType valueType,
        String category,
        String description
) {}