package org.github.tess1o.geopulse.streaming.service;

import org.github.tess1o.geopulse.streaming.model.domain.DataGap;
import org.github.tess1o.geopulse.streaming.model.domain.RawTimeline;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class StreamingTimelineGenerationServiceTest {

    @Test
    void timelineDataChangedRangeIncludesBackdatedRegeneratedEvents() {
        Instant fallbackFrom = Instant.parse("2026-08-22T17:21:53Z");
        Instant fallbackTo = Instant.parse("2026-08-28T20:07:01Z");
        RawTimeline rawTimeline = RawTimeline.builder()
                .trips(List.of(Trip.builder()
                        .startTime(Instant.parse("2026-08-13T05:44:50Z"))
                        .duration(Duration.ofSeconds(2_974))
                        .tripType(TripType.CAR)
                        .build()))
                .build();

        var range = StreamingTimelineGenerationService.calculateTimelineDataRange(
                fallbackFrom, fallbackTo, rawTimeline);

        assertThat(range.affectedFrom()).isEqualTo(Instant.parse("2026-08-13T05:44:50Z"));
        assertThat(range.affectedTo()).isEqualTo(fallbackTo);
    }

    @Test
    void timelineDataChangedRangeIncludesLatestGeneratedEventEnd() {
        Instant fallbackFrom = Instant.parse("2026-08-22T17:21:53Z");
        Instant fallbackTo = Instant.parse("2026-08-28T20:07:01Z");
        RawTimeline rawTimeline = RawTimeline.builder()
                .stays(List.of(Stay.builder()
                        .startTime(Instant.parse("2026-08-28T20:30:00Z"))
                        .duration(Duration.ofMinutes(45))
                        .build()))
                .trips(List.of(Trip.builder()
                        .startTime(Instant.parse("2026-08-28T21:30:00Z"))
                        .duration(Duration.ofMinutes(10))
                        .tripType(TripType.CAR)
                        .build()))
                .dataGaps(List.of(DataGap.builder()
                        .startTime(Instant.parse("2026-08-28T22:00:00Z"))
                        .duration(Duration.ofHours(2))
                        .build()))
                .build();

        var range = StreamingTimelineGenerationService.calculateTimelineDataRange(
                fallbackFrom, fallbackTo, rawTimeline);

        assertThat(range.affectedFrom()).isEqualTo(fallbackFrom);
        assertThat(range.affectedTo()).isEqualTo(Instant.parse("2026-08-29T00:00:00Z"));
    }

    @Test
    void timelineDataChangedRangeFallsBackWhenNoEventsWereGenerated() {
        Instant fallbackFrom = Instant.parse("2026-08-22T17:21:53Z");
        Instant fallbackTo = Instant.parse("2026-08-28T20:07:01Z");

        var range = StreamingTimelineGenerationService.calculateTimelineDataRange(
                fallbackFrom, fallbackTo, RawTimeline.builder().build());

        assertThat(range.affectedFrom()).isEqualTo(fallbackFrom);
        assertThat(range.affectedTo()).isEqualTo(fallbackTo);
    }
}
