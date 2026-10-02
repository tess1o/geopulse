package org.github.tess1o.geopulse.sharing.model;

import org.github.tess1o.geopulse.shared.map.MapColorScheme;
import org.github.tess1o.geopulse.user.model.TimelineDisplayPreferences;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("unit")
class SharedMapAppearanceTest {

    @Test
    void copiesOnlyTheOwnersAppearance() {
        SharedMapAppearance appearance = SharedMapAppearance.from(TimelineDisplayPreferences.builder()
                .colorScheme(MapColorScheme.RED_GREEN_SAFE)
                .activePathColor("#ffcc00")
                .speedBandPalette("OFF")
                .pathOutlineEnabled(true)
                .pathWidth(6)
                .customMapTileUrl("https://tiles.example.com/{z}/{x}/{y}.png")
                .build());

        assertEquals(MapColorScheme.RED_GREEN_SAFE, appearance.getColorScheme());
        assertEquals("#ffcc00", appearance.getActivePathColor());
        assertEquals("OFF", appearance.getSpeedBandPalette());
        assertEquals(true, appearance.getPathOutlineEnabled());
        assertEquals(6, appearance.getPathWidth());
        assertNull(appearance.getDefaultPathColor());
        assertNull(appearance.getHeatmapGradient());
    }

    @Test
    void fallsBackToDefaultsWhenTheOwnerSetNothing() {
        SharedMapAppearance appearance = SharedMapAppearance.from(null);

        assertEquals(MapColorScheme.DEFAULT, appearance.getColorScheme());
        assertEquals(false, appearance.getPathOutlineEnabled());
        assertEquals(4, appearance.getPathWidth());
        assertNull(appearance.getDefaultPathColor());
    }
}
