package org.github.tess1o.geopulse.importdata.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * What a debug import found: the user's description of the problem, the state of their timeline and GPS sources when
 * they exported, and how the regenerated timeline compares with the one they saw.
 *
 * @param gpsSources     filter settings of the user's GPS sources, as exported
 * @param timelineStatus when the user's timeline was built and whether a rebuild was pending, as exported
 * @param warnings       reasons the regenerated timeline may differ from the user's for reasons other than a bug
 */
public record DebugImportReport(String exportVersion,
                                String exporterAppVersion,
                                String importerAppVersion,
                                Instant exportDate,
                                String timezone,
                                boolean timezoneApplied,
                                boolean timelineConfigApplied,
                                Issue issue,
                                Range requestedRange,
                                Range exportedRange,
                                Imported imported,
                                List<Map<String, Object>> gpsSources,
                                Map<String, Object> timelineStatus,
                                Water water,
                                DebugTimelineComparison comparison,
                                List<Warning> warnings) {

    /** What the user wrote about the problem and when it happened. */
    public record Issue(String description, Instant time) {
    }

    public record Range(Instant start, Instant end) {
    }

    public record Imported(long gpsPoints,
                           int favoriteLocations,
                           int favoriteAreas,
                           int geocodingLocations,
                           boolean overrides) {
    }

    /**
     * @param boatEnabled             boat detection was on for the user
     * @param exporterDatasetAvailable the exporting server had water data, so points carry water flags
     * @param importerDatasetAvailable this server has water data, so the flags could be used
     * @param flagsImported           points whose water flag was imported
     */
    public record Water(boolean boatEnabled,
                        boolean exporterDatasetAvailable,
                        boolean importerDatasetAvailable,
                        long flagsImported) {
    }

    /** A translatable warning: {@code code} names it, {@code params} fill it in. */
    public record Warning(String code, Map<String, Object> params) {
        public static Warning of(String code) {
            return new Warning(code, Map.of());
        }
    }
}
