package org.github.tess1o.geopulse.user.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.github.tess1o.geopulse.shared.map.MapColorScheme;
import org.github.tess1o.geopulse.shared.map.PathColorMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("unit")
class TimelineDisplayPreferencesTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @ValueSource(strings = {"", "DEFAULT", "RED_GREEN_SAFE", "BLUE_YELLOW_SAFE", "HIGH_CONTRAST", "OFF"})
    void acceptsKnownSpeedBandPalettes(String palette) {
        assertEquals(0, validator.validate(TimelineDisplayPreferences.builder().speedBandPalette(palette).build()).size());
    }

    @Test
    void rejectsUnknownSpeedBandPalette() {
        assertEquals(1, validator.validate(TimelineDisplayPreferences.builder().speedBandPalette("PURPLE").build()).size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "CLASSIC", "VIRIDIS", "CIVIDIS"})
    void acceptsKnownHeatmapGradients(String gradient) {
        assertEquals(0, validator.validate(TimelineDisplayPreferences.builder().heatmapGradient(gradient).build()).size());
    }

    @Test
    void rejectsUnknownHeatmapGradient() {
        assertEquals(1, validator.validate(TimelineDisplayPreferences.builder().heatmapGradient("RAINBOW").build()).size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "CLASSIC", "OKABE_ITO", "TOL_MUTED", "CONTRAST"})
    void acceptsKnownDayPalettes(String palette) {
        assertEquals(0, validator.validate(TimelineDisplayPreferences.builder().dayPalette(palette).build()).size());
    }

    @Test
    void rejectsUnknownDayPalette() {
        assertEquals(1, validator.validate(TimelineDisplayPreferences.builder().dayPalette("RAINBOW").build()).size());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 11})
    void rejectsPathWidthOutOfRange(int width) {
        assertEquals(1, validator.validate(TimelineDisplayPreferences.builder().pathWidth(width).build()).size());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 4, 10})
    void acceptsPathWidthInRange(int width) {
        assertEquals(0, validator.validate(TimelineDisplayPreferences.builder().pathWidth(width).build()).size());
    }

    @Test
    void withDefaultsFillsAppearanceButLeavesSchemeFollowersUnset() {
        TimelineDisplayPreferences resolved = new TimelineDisplayPreferences().withDefaults();

        assertEquals(MapColorScheme.DEFAULT, resolved.getColorScheme());
        assertEquals(false, resolved.getPathOutlineEnabled());
        assertEquals(4, resolved.getPathWidth());
        assertEquals(PathColorMode.SINGLE, resolved.getPathColorMode());
        assertEquals(true, resolved.getShowMapScale());
        assertNull(resolved.getDayPalette());
        assertNull(resolved.getSpeedBandPalette());
        assertNull(resolved.getHeatmapGradient());
        assertNull(resolved.getDefaultPathColor());
        assertNull(resolved.getActivePathColor());
    }

    @Test
    void withDefaultsKeepsStoredAppearance() {
        TimelineDisplayPreferences resolved = TimelineDisplayPreferences.builder()
                .colorScheme(MapColorScheme.RED_GREEN_SAFE)
                .pathOutlineEnabled(true)
                .pathWidth(7)
                .speedBandPalette("OFF")
                .pathColorMode(PathColorMode.DAY)
                .dayPalette("TOL_MUTED")
                .showMapScale(false)
                .build()
                .withDefaults();

        assertEquals(MapColorScheme.RED_GREEN_SAFE, resolved.getColorScheme());
        assertEquals(true, resolved.getPathOutlineEnabled());
        assertEquals(7, resolved.getPathWidth());
        assertEquals("OFF", resolved.getSpeedBandPalette());
        assertEquals(PathColorMode.DAY, resolved.getPathColorMode());
        assertEquals("TOL_MUTED", resolved.getDayPalette());
        assertEquals(false, resolved.getShowMapScale());
    }
}
