package org.github.tess1o.geopulse.user.model;

import com.fasterxml.jackson.annotation.JsonInclude;
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
import org.github.tess1o.geopulse.streaming.model.shared.TripType;

import java.io.Serializable;
import java.util.List;

/**
 * Timeline display preferences - settings that affect ONLY how timelines are rendered in the UI.
 * These settings do NOT affect timeline generation and changing them does NOT trigger regeneration.
 * This is in contrast to {@link TimelinePreferences}, which holds processing/generation parameters.
 *
 * <p>Stored as the {@code users.timeline_display_preferences} JSONB document. The same type is the stored value,
 * the update body and the response payload, so adding a preference means adding a field here and, if it has one,
 * a default in {@link #withDefaults()}. Only values the user has set are stored; {@code null} means "use the
 * default". Server-derived state (what the administrator made available) lives in
 * {@link TimelineDisplayCapabilities}, not here.</p>
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimelineDisplayPreferences implements Serializable {

    /** Custom raster tile URL. Null uses the default OpenStreetMap tiles; empty string resets it. */
    @Size(max = 1000, message = "Custom map tile URL cannot exceed 1000 characters")
    private String customMapTileUrl;

    /** Custom vector style URL. Null uses the app default style; empty string resets it. */
    @Size(max = 1000, message = "Custom map style URL cannot exceed 1000 characters")
    private String customMapStyleUrl;

    /** Default: VECTOR. */
    private MapRenderMode mapRenderMode;

    /** Enable GPS path simplification when rendering paths. Default: true. */
    private Boolean pathSimplificationEnabled;

    /** Douglas-Peucker tolerance in meters. Default: 15.0. */
    @DecimalMin(value = "1.0", message = "Path simplification tolerance must be at least 1.0 meters")
    @DecimalMax(value = "100.0", message = "Path simplification tolerance cannot exceed 100.0 meters")
    private Double pathSimplificationTolerance;

    /** Maximum number of points to display in a path (0 = unlimited). Default: 0. */
    @Min(value = 0, message = "Path max points must be at least 0")
    @Max(value = 1000, message = "Path max points cannot exceed 1000")
    private Integer pathMaxPoints;

    /** Enable adaptive simplification based on zoom level. Default: true. */
    private Boolean pathAdaptiveSimplification;

    /** Default date range for Timeline, Dashboard and Timeline Reports. Null falls back to today. */
    @Pattern(regexp = "^(today|yesterday|lastWeek|lastMonth)?$",
            message = "Default date range preset must be one of: today, yesterday, lastWeek, lastMonth")
    private String defaultDateRangePreset;

    /** Show telemetry in the current-location popup on the Timeline map. Default: true. */
    private Boolean showCurrentLocationTelemetry;

    /** Automatically show trip replay controls when a trip is selected. Default: true. */
    private Boolean autoShowTripReplayControls;

    /** Enable 3D buildings when a compatible MapTiler vector map opens. Default: false. */
    private Boolean enable3dBuildingsByDefault;

    /** Use cached Valhalla map matching for trip path display. Default: false. */
    private Boolean mapMatchingEnabled;

    /** Movement types that keep displaying raw GPS even when map matching is enabled. Empty list clears it. */
    private List<TripType> mapMatchingExcludedMovementTypes;

    /** Hex color (#rrggbb) for the normal timeline path. Null uses the app default (#007bff); empty string resets it. */
    @Pattern(regexp = "^(#[0-9A-Fa-f]{6})?$", message = "Default path color must be a hex color like #007bff, or empty to reset to default")
    private String defaultPathColor;

    /** Hex color (#rrggbb) for the highlighted trip path. Null uses the app default (#ef4444); empty string resets it. */
    @Pattern(regexp = "^(#[0-9A-Fa-f]{6})?$", message = "Active path color must be a hex color like #ef4444, or empty to reset to default")
    private String activePathColor;

    /** A copy with every unset preference replaced by its application default. */
    public TimelineDisplayPreferences withDefaults() {
        return toBuilder()
                .mapRenderMode(mapRenderMode != null ? mapRenderMode : MapRenderMode.VECTOR)
                .pathSimplificationEnabled(pathSimplificationEnabled != null ? pathSimplificationEnabled : true)
                .pathSimplificationTolerance(pathSimplificationTolerance != null ? pathSimplificationTolerance : 15.0)
                .pathMaxPoints(pathMaxPoints != null ? pathMaxPoints : 0)
                .pathAdaptiveSimplification(pathAdaptiveSimplification != null ? pathAdaptiveSimplification : true)
                .showCurrentLocationTelemetry(showCurrentLocationTelemetry != null ? showCurrentLocationTelemetry : true)
                .autoShowTripReplayControls(autoShowTripReplayControls != null ? autoShowTripReplayControls : true)
                .enable3dBuildingsByDefault(enable3dBuildingsByDefault != null ? enable3dBuildingsByDefault : false)
                .mapMatchingEnabled(mapMatchingEnabled != null ? mapMatchingEnabled : false)
                .mapMatchingExcludedMovementTypes(mapMatchingExcludedMovementTypes != null
                        ? mapMatchingExcludedMovementTypes : List.of())
                .build();
    }
}
