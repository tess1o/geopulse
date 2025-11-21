package org.github.tess1o.geopulse.admin.model;

/**
 * Categories for system settings.
 */
public enum SettingsCategory {
    AUTH("auth"),
    GEOCODING("geocoding"),
    GPS("gps"),
    IMPORT("import"),
    SYSTEM("system");

    private final String value;

    SettingsCategory(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SettingsCategory fromValue(String value) {
        for (SettingsCategory category : values()) {
            if (category.value.equalsIgnoreCase(value)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown category: " + value);
    }
}
