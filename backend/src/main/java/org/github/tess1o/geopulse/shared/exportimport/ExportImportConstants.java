package org.github.tess1o.geopulse.shared.exportimport;

/**
 * Constants for export/import functionality to ensure consistency between
 * export and import services and avoid naming mismatches.
 */
public final class ExportImportConstants {

    private ExportImportConstants() {
        // Utility class
    }

    /**
     * Data type identifiers used in export/import operations
     */
    public static final class DataTypes {
        public static final String RAW_GPS = "rawgps";
        public static final String TIMELINE = "timeline";
        public static final String DATA_GAPS = "datagaps";
        public static final String FAVORITES = "favorites";
        public static final String USER_INFO = "userinfo";
        public static final String LOCATION_SOURCES = "locationsources";
        public static final String REVERSE_GEOCODING_LOCATION = "reversegeocodinglocation";
    }

    /**
     * File names used in export ZIP files
     */
    public static final class FileNames {
        public static final String METADATA = "metadata.json";
        public static final String RAW_GPS_DATA = "raw-gps-data.json";
        public static final String TIMELINE_DATA = "timeline-data.json";
        public static final String DATA_GAPS = "data-gaps.json";
        public static final String FAVORITES = "favorites.json";
        public static final String USER_INFO = "user-info.json";
        public static final String LOCATION_SOURCES = "location-sources.json";
        public static final String REVERSE_GEOCODING = "reverse-geocoding.json";
    }

    /**
     * Export format constants
     */
    public static final class Formats {
        public static final String JSON = "json";
        public static final String GEOPULSE = "geopulse";
    }

    /**
     * Export version constants
     */
    public static final class Versions {
        public static final String CURRENT = "1.0";
    }
}