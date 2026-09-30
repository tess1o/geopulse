package org.github.tess1o.geopulse.user.service;

import org.github.tess1o.geopulse.shared.map.MapColorScheme;
import org.github.tess1o.geopulse.shared.map.MapRenderMode;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.user.model.DistanceUnit;
import org.github.tess1o.geopulse.user.model.TimelineDisplayPreferences;
import org.github.tess1o.geopulse.user.model.UserUiPreferences;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("unit")
class PreferencePatchesTest {

    @Test
    void nullFieldsLeaveStoredValuesUnchanged() {
        TimelineDisplayPreferences current = TimelineDisplayPreferences.builder()
                .pathMaxPoints(250)
                .mapRenderMode(MapRenderMode.RASTER)
                .defaultPathColor("#112233")
                .build();

        TimelineDisplayPreferences merged = PreferencePatches.apply(current,
                TimelineDisplayPreferences.builder().enable3dBuildingsByDefault(true).build());

        assertEquals(250, merged.getPathMaxPoints());
        assertEquals(MapRenderMode.RASTER, merged.getMapRenderMode());
        assertEquals("#112233", merged.getDefaultPathColor());
        assertEquals(true, merged.getEnable3dBuildingsByDefault());
    }

    @Test
    void nonNullFieldsReplaceStoredValues() {
        TimelineDisplayPreferences current = TimelineDisplayPreferences.builder()
                .pathSimplificationEnabled(true)
                .pathSimplificationTolerance(15.0)
                .mapRenderMode(MapRenderMode.VECTOR)
                .build();

        TimelineDisplayPreferences merged = PreferencePatches.apply(current, TimelineDisplayPreferences.builder()
                .pathSimplificationEnabled(false)
                .pathSimplificationTolerance(42.5)
                .mapRenderMode(MapRenderMode.RASTER)
                .build());

        assertEquals(false, merged.getPathSimplificationEnabled());
        assertEquals(42.5, merged.getPathSimplificationTolerance());
        assertEquals(MapRenderMode.RASTER, merged.getMapRenderMode());
    }

    @Test
    void falseAndZeroAreValuesNotAbsence() {
        TimelineDisplayPreferences current = TimelineDisplayPreferences.builder()
                .showCurrentLocationTelemetry(true)
                .pathMaxPoints(500)
                .build();

        TimelineDisplayPreferences merged = PreferencePatches.apply(current, TimelineDisplayPreferences.builder()
                .showCurrentLocationTelemetry(false)
                .pathMaxPoints(0)
                .build());

        assertEquals(false, merged.getShowCurrentLocationTelemetry());
        assertEquals(0, merged.getPathMaxPoints());
    }

    @Test
    void blankStringRemovesTheStoredValue() {
        TimelineDisplayPreferences current = TimelineDisplayPreferences.builder()
                .defaultPathColor("#112233")
                .customMapTileUrl("https://tiles.example.com/{z}/{x}/{y}.png")
                .build();

        TimelineDisplayPreferences merged = PreferencePatches.apply(current, TimelineDisplayPreferences.builder()
                .defaultPathColor("")
                .customMapTileUrl("   ")
                .build());

        assertNull(merged.getDefaultPathColor());
        assertNull(merged.getCustomMapTileUrl());
    }

    @Test
    void appearancePatchKeepsOtherStoredAppearance() {
        TimelineDisplayPreferences current = TimelineDisplayPreferences.builder()
                .defaultPathColor("#112233")
                .colorScheme(MapColorScheme.DEFAULT)
                .speedBandPalette("OFF")
                .pathOutlineEnabled(true)
                .build();

        TimelineDisplayPreferences merged = PreferencePatches.apply(current, TimelineDisplayPreferences.builder()
                .colorScheme(MapColorScheme.RED_GREEN_SAFE)
                .speedBandPalette("")
                .pathOutlineEnabled(false)
                .pathWidth(6)
                .build());

        assertEquals("#112233", merged.getDefaultPathColor());
        assertEquals(MapColorScheme.RED_GREEN_SAFE, merged.getColorScheme());
        assertNull(merged.getSpeedBandPalette());
        assertEquals(false, merged.getPathOutlineEnabled());
        assertEquals(6, merged.getPathWidth());
    }

    @Test
    void stringsAreTrimmed() {
        UserUiPreferences merged = PreferencePatches.apply(new UserUiPreferences(), UserUiPreferences.builder()
                .defaultRedirectUrl("  /app/dashboard  ")
                .build());

        assertEquals("/app/dashboard", merged.getDefaultRedirectUrl());
    }

    @Test
    void listsAreReplacedNotMerged() {
        TimelineDisplayPreferences current = TimelineDisplayPreferences.builder()
                .mapMatchingExcludedMovementTypes(List.of(TripType.WALK, TripType.CAR))
                .build();

        TimelineDisplayPreferences merged = PreferencePatches.apply(current, TimelineDisplayPreferences.builder()
                .mapMatchingExcludedMovementTypes(List.of(TripType.BICYCLE))
                .build());

        assertEquals(List.of(TripType.BICYCLE), merged.getMapMatchingExcludedMovementTypes());
    }

    @Test
    void emptyListClearsTheStoredList() {
        TimelineDisplayPreferences current = TimelineDisplayPreferences.builder()
                .mapMatchingExcludedMovementTypes(List.of(TripType.WALK))
                .build();

        TimelineDisplayPreferences merged = PreferencePatches.apply(current, TimelineDisplayPreferences.builder()
                .mapMatchingExcludedMovementTypes(List.of())
                .build());

        assertEquals(List.of(), merged.getMapMatchingExcludedMovementTypes());
    }

    @Test
    void nullPatchReturnsAnEqualCopy() {
        UserUiPreferences current = UserUiPreferences.builder()
                .distanceUnit(DistanceUnit.MILES)
                .dateFormat("DMY")
                .build();

        UserUiPreferences merged = PreferencePatches.apply(current, null);

        assertEquals(current, merged);
        assertNotSame(current, merged);
    }

    @Test
    void doesNotMutateTheCurrentValue() {
        TimelineDisplayPreferences current = TimelineDisplayPreferences.builder()
                .defaultPathColor("#112233")
                .pathMaxPoints(250)
                .build();

        TimelineDisplayPreferences merged = PreferencePatches.apply(current, TimelineDisplayPreferences.builder()
                .defaultPathColor("")
                .pathMaxPoints(10)
                .build());

        assertNotSame(current, merged);
        assertEquals("#112233", current.getDefaultPathColor());
        assertEquals(250, current.getPathMaxPoints());
    }

    @Test
    void emptyPatchOnEmptyCurrentStaysEmpty() {
        TimelineDisplayPreferences merged = PreferencePatches.apply(
                new TimelineDisplayPreferences(), new TimelineDisplayPreferences());

        assertEquals(new TimelineDisplayPreferences(), merged);
    }
}
