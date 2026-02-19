package org.github.tess1o.geopulse.digest.model;

public enum HeatmapLayer {
    STAYS,
    TRIPS,
    COMBINED;

    public static HeatmapLayer fromString(String value) {
        if (value == null || value.isBlank()) {
            return COMBINED;
        }
        try {
            return HeatmapLayer.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
