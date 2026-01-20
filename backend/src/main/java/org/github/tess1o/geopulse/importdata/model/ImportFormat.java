package org.github.tess1o.geopulse.importdata.model;

import java.util.Locale;
import java.util.Set;

/**
 * Enum representing supported import formats with their allowed file extensions.
 * Used for unified validation across all upload endpoints.
 */
public enum ImportFormat {
    OWNTRACKS("owntracks", Set.of(".json"), "owntracks-import.json"),
    GPX("gpx", Set.of(".gpx"), "gpx-import.gpx"),
    GPX_ZIP("gpx-zip", Set.of(".zip"), "gpx-import.zip"),
    GOOGLE_TIMELINE("google-timeline", Set.of(".json"), "google-timeline-import.json"),
    GEOJSON("geojson", Set.of(".json", ".geojson"), "geojson-import.geojson"),
    CSV("csv", Set.of(".csv"), "csv-import.csv"),
    GEOPULSE("geopulse", Set.of(".zip"), "geopulse-import.zip");

    private final String value;
    private final Set<String> allowedExtensions;
    private final String defaultFileName;

    ImportFormat(String value, Set<String> allowedExtensions, String defaultFileName) {
        this.value = value;
        this.allowedExtensions = allowedExtensions;
        this.defaultFileName = defaultFileName;
    }

    public String getValue() {
        return value;
    }

    public Set<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    /**
     * Get the default file name for this format (used when no file name is provided).
     */
    public String getDefaultFileName() {
        return defaultFileName;
    }

    /**
     * Check if a file name has a valid extension for this format.
     * @param fileName The file name to check
     * @return true if the extension is valid for this format
     */
    public boolean isValidExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String lowerFileName = fileName.toLowerCase(Locale.ENGLISH);
        return allowedExtensions.stream().anyMatch(lowerFileName::endsWith);
    }

    /**
     * Get the format from a string value.
     * @param value The format value (e.g., "owntracks", "gpx", "google-timeline")
     * @return The ImportFormat or null if not found
     */
    public static ImportFormat fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String lowerValue = value.toLowerCase(Locale.ENGLISH).trim();
        for (ImportFormat format : values()) {
            if (format.value.equals(lowerValue)) {
                return format;
            }
        }
        return null;
    }

    /**
     * Get a comma-separated list of all supported format values.
     * Useful for error messages.
     */
    public static String getSupportedFormats() {
        StringBuilder sb = new StringBuilder();
        for (ImportFormat format : values()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(format.value);
        }
        return sb.toString();
    }

    /**
     * Auto-detect format from file name for GPX files.
     * GPX files can be uploaded as .gpx (single file) or .zip (multiple files).
     * @param fileName The file name
     * @param requestedFormat The format requested by the user
     * @return The actual format to use (may be GPX_ZIP if .zip extension detected)
     */
    public static ImportFormat resolveGpxFormat(String fileName, ImportFormat requestedFormat) {
        if (requestedFormat == GPX && fileName != null) {
            String lowerFileName = fileName.toLowerCase(Locale.ENGLISH);
            if (lowerFileName.endsWith(".zip")) {
                return GPX_ZIP;
            }
        }
        return requestedFormat;
    }
}
