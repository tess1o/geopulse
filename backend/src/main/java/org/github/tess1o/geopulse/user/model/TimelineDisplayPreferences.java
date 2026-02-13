package org.github.tess1o.geopulse.user.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Timeline display preferences - settings that affect ONLY how timelines are rendered in the UI.
 * These settings do NOT affect timeline generation and changing them does NOT trigger regeneration.
 *
 * This is in contrast to TimelinePreferences which contains processing/generation parameters.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimelineDisplayPreferences {

    /**
     * Custom map tile URL for displaying the map.
     * Default: null (use default OpenStreetMap tiles)
     */
    private String customMapTileUrl;

    /**
     * Enable GPS path simplification when rendering paths in UI.
     * Default: true
     */
    private Boolean pathSimplificationEnabled;

    /**
     * Douglas-Peucker tolerance for path simplification in meters.
     * Valid range: 1.0 to 100.0
     * Default: 15.0
     */
    private Double pathSimplificationTolerance;

    /**
     * Maximum number of points to display in a path (0 = unlimited).
     * Valid range: 0 to 1000
     * Default: 0
     */
    private Integer pathMaxPoints;

    /**
     * Enable adaptive simplification based on zoom level.
     * Default: true
     */
    private Boolean pathAdaptiveSimplification;
}
