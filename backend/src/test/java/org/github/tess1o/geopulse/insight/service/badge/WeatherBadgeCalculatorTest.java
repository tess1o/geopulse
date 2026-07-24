package org.github.tess1o.geopulse.insight.service.badge;

import org.github.tess1o.geopulse.insight.model.Badge;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class WeatherBadgeCalculatorTest {

    private final UUID userId = UUID.randomUUID();

    @Mock
    WeatherBadgeQueryService queryService;

    @Test
    void weatherWitnessRequiresOneSample() {
        WeatherWitnessBadgeCalculator calculator = new WeatherWitnessBadgeCalculator(queryService);

        Badge unearned = calculator.calculateBadge(userId);
        assertFalse(unearned.isEarned());
        assertEquals(0, unearned.getCurrent());
        assertNull(unearned.getEarnedDate());

        when(queryService.countSamples(userId)).thenReturn(1L);
        when(queryService.firstSampleAt(userId)).thenReturn(Instant.parse("2026-01-01T10:00:00Z"));

        Badge earned = calculator.calculateBadge(userId);
        assertTrue(earned.isEarned());
        assertEquals("2026-01-01", earned.getEarnedDate());
    }

    @Test
    void rainTravelerRequiresTenRainySamples() {
        RainTravelerBadgeCalculator calculator = new RainTravelerBadgeCalculator(queryService);

        when(queryService.countRainySamples(userId)).thenReturn(9L);
        Badge unearned = calculator.calculateBadge(userId);
        assertFalse(unearned.isEarned());
        assertEquals(90, unearned.getProgress());

        when(queryService.countRainySamples(userId)).thenReturn(10L);
        when(queryService.nthRainySampleAt(userId, 10)).thenReturn(Instant.parse("2026-04-11T08:00:00Z"));

        Badge earned = calculator.calculateBadge(userId);
        assertTrue(earned.isEarned());
        assertEquals("2026-04-11", earned.getEarnedDate());
    }

    @Test
    void heatwaveExplorerRequiresThirtyCelsiusSample() {
        HeatwaveExplorerBadgeCalculator calculator = new HeatwaveExplorerBadgeCalculator(queryService);

        when(queryService.maxTemperature(userId)).thenReturn(29.4);
        Badge unearned = calculator.calculateBadge(userId);
        assertFalse(unearned.isEarned());
        assertEquals(29, unearned.getCurrent());

        when(queryService.maxTemperature(userId)).thenReturn(30.0);
        when(queryService.firstSampleAtOrAboveTemperature(userId, 30.0))
                .thenReturn(Instant.parse("2026-07-20T14:00:00Z"));

        Badge earned = calculator.calculateBadge(userId);
        assertTrue(earned.isEarned());
        assertEquals("2026-07-20", earned.getEarnedDate());
    }

    @Test
    void frostWalkerRequiresZeroCelsiusOrColderSample() {
        FrostWalkerBadgeCalculator calculator = new FrostWalkerBadgeCalculator(queryService);

        when(queryService.minTemperature(userId)).thenReturn(1.0);
        Badge unearned = calculator.calculateBadge(userId);
        assertFalse(unearned.isEarned());
        assertEquals(0, unearned.getProgress());

        when(queryService.minTemperature(userId)).thenReturn(0.0);
        when(queryService.firstSampleAtOrBelowTemperature(userId, 0.0))
                .thenReturn(Instant.parse("2026-02-01T06:00:00Z"));

        Badge earned = calculator.calculateBadge(userId);
        assertTrue(earned.isEarned());
        assertEquals("2026-02-01", earned.getEarnedDate());
    }

    @Test
    void fourSeasonsRequiresSamplesInAllMeteorologicalSeasons() {
        FourSeasonsBadgeCalculator calculator = new FourSeasonsBadgeCalculator(queryService);

        when(queryService.seasonCoverage(userId))
                .thenReturn(new WeatherBadgeQueryService.WeatherSeasonCoverage(3, null));
        Badge unearned = calculator.calculateBadge(userId);
        assertFalse(unearned.isEarned());
        assertEquals(75, unearned.getProgress());

        when(queryService.seasonCoverage(userId))
                .thenReturn(new WeatherBadgeQueryService.WeatherSeasonCoverage(
                        4,
                        Instant.parse("2026-12-02T11:00:00Z")
                ));

        Badge earned = calculator.calculateBadge(userId);
        assertTrue(earned.isEarned());
        assertEquals("2026-12-02", earned.getEarnedDate());
    }
}
