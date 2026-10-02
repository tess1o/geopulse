package org.github.tess1o.geopulse.sharing.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.shared.map.MapColorScheme;
import org.github.tess1o.geopulse.user.model.TimelineDisplayPreferences;

/**
 * The link owner's map appearance, read live from their profile on every load so a shared map looks the way
 * the owner sees it. Only appearance is exposed; the rest of {@link TimelineDisplayPreferences} stays private.
 * Null values mean "follow colorScheme", exactly as in the owner's own preferences.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SharedMapAppearance {

    private MapColorScheme colorScheme;
    private String defaultPathColor;
    private String activePathColor;
    private String speedBandPalette;
    private String heatmapGradient;
    private Boolean pathOutlineEnabled;
    private Integer pathWidth;

    public static SharedMapAppearance from(TimelineDisplayPreferences preferences) {
        TimelineDisplayPreferences resolved = (preferences != null ? preferences : new TimelineDisplayPreferences())
                .withDefaults();
        return SharedMapAppearance.builder()
                .colorScheme(resolved.getColorScheme())
                .defaultPathColor(resolved.getDefaultPathColor())
                .activePathColor(resolved.getActivePathColor())
                .speedBandPalette(resolved.getSpeedBandPalette())
                .heatmapGradient(resolved.getHeatmapGradient())
                .pathOutlineEnabled(resolved.getPathOutlineEnabled())
                .pathWidth(resolved.getPathWidth())
                .build();
    }
}
