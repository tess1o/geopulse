package org.github.tess1o.geopulse.user.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
