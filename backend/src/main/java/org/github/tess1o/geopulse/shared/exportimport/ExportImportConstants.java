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
        public static final String PERIOD_TAGS = "periodtags";
        public static final String TIMELINE_OVERRIDES = "timelineoverrides";
        public static final String TRIP_WORKSPACE = "tripworkspace";
        public static final String NOTIFICATION_TEMPLATES = "notificationtemplates";
        public static final String GEOFENCING = "geofencing";
        public static final String NOTES = "notes";
        public static final String WEATHER_SAMPLES = "weathersamples";
        public static final String MAP_MATCHING = "mapmatching";
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
        public static final String PERIOD_TAGS = "period-tags.json";
        public static final String TIMELINE_OVERRIDES = "timeline-overrides.json";
        public static final String TRIP_WORKSPACE = "trip-workspace.json";
        public static final String NOTIFICATION_TEMPLATES = "notification-templates.json";
        public static final String GEOFENCING = "geofencing.json";
        public static final String NOTES = "notes.json";
        public static final String WEATHER_SAMPLES = "weather-samples.json";
        public static final String MAP_MATCHING = "map-matching.json";
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
        public static final String CURRENT = "1.1";
        public static final String V1_0 = "1.0";
    }
}
