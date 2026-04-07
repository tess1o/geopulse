package org.github.tess1o.geopulse.user.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.shared.map.MapRenderMode;

/**
 * Request DTO for updating timeline display preferences.
 * These settings affect ONLY how timelines are rendered in the UI.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateTimelineDisplayPreferencesRequest {

    /**
     * Custom map tile URL for displaying the map.
     * Valid range: 0 to 1000 characters
     */
    @Size(max = 1000, message = "Custom map tile URL cannot exceed 1000 characters")
    private String customMapTileUrl;

    /**
     * Custom vector map style URL for displaying the map.
     * Valid range: 0 to 1000 characters.
     */
    @Size(max = 1000, message = "Custom map style URL cannot exceed 1000 characters")
    private String customMapStyleUrl;

    /**
     * Preferred map rendering mode.
     */
    private MapRenderMode mapRenderMode;

    /**
     * Enable GPS path simplification when rendering paths in UI.
     */
    private Boolean pathSimplificationEnabled;

    /**
     * Douglas-Peucker tolerance for path simplification in meters.
     * Valid range: 1.0 to 100.0
     */
    @DecimalMin(value = "1.0", message = "Path simplification tolerance must be at least 1.0 meters")
    @DecimalMax(value = "100.0", message = "Path simplification tolerance cannot exceed 100.0 meters")
    private Double pathSimplificationTolerance;

    /**
     * Maximum number of points to display in a path (0 = unlimited).
     * Valid range: 0 to 1000
     */
    @Min(value = 0, message = "Path max points must be at least 0")
    @Max(value = 1000, message = "Path max points cannot exceed 1000")
    private Integer pathMaxPoints;

    /**
     * Enable adaptive simplification based on zoom level.
     */
    private Boolean pathAdaptiveSimplification;

    /**
     * Default preset used for Timeline, Dashboard and Timeline Reports date range.
     * Valid values: today, yesterday, lastWeek, lastMonth
     */
    @Pattern(regexp = "^(today|yesterday|lastWeek|lastMonth)?$",
            message = "Default date range preset must be one of: today, yesterday, lastWeek, lastMonth")
    private String defaultDateRangePreset;

    /**
     * Show telemetry in the current-location popup on Timeline map.
     */
    private Boolean showCurrentLocationTelemetry;
}
