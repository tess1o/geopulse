package org.github.tess1o.geopulse.mapmatching.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class MapMatchingBackfillProgressTest {

    @Test
    void calculatesBoundedTripAndUserProgress() {
        MapMatchingBackfillProgress progress = new MapMatchingBackfillProgress(
                18_341, 12_850, 49, 13, Instant.parse("2026-08-22T21:00:00Z"));

        assertThat(progress.remainingTrips()).isEqualTo(5_491);
        assertThat(progress.remainingUsers()).isEqualTo(36);
        assertThat(progress.percent()).isEqualTo(70.06161059920397);
    }

    @Test
    void reportsEmptyCompletedBackfillAsOneHundredPercent() {
        MapMatchingBackfillProgress progress = new MapMatchingBackfillProgress(
                0, 0, 2, 2, null);

        assertThat(progress.remainingTrips()).isZero();
        assertThat(progress.remainingUsers()).isZero();
        assertThat(progress.percent()).isEqualTo(100.0);
    }
}
