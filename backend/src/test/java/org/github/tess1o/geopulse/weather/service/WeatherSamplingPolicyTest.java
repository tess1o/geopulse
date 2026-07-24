package org.github.tess1o.geopulse.weather.service;

import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class WeatherSamplingPolicyTest {

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private final WeatherSamplingPolicy policy = new WeatherSamplingPolicy();

    @Test
    void shortStayGetsOneMidpointSample() {
        TimelineStayEntity stay = TimelineStayEntity.builder()
                .timestamp(Instant.parse("2026-07-23T10:15:00Z"))
                .stayDuration(60 * 60)
                .location(geometryFactory.createPoint(new Coordinate(30.5234, 50.4501)))
                .build();

        List<WeatherSampleCandidate> samples = policy.forStay(stay, WeatherTargetSource.HISTORICAL_BACKFILL, 10);

        assertThat(samples).hasSize(1);
        assertThat(samples.getFirst().targetAt()).isEqualTo(Instant.parse("2026-07-23T10:00:00Z"));
        assertThat(samples.getFirst().latitude()).isEqualTo(50.4501);
        assertThat(samples.getFirst().longitude()).isEqualTo(30.5234);
    }

    @Test
    void eightHourStayGetsFourSamples() {
        TimelineStayEntity stay = TimelineStayEntity.builder()
                .timestamp(Instant.parse("2026-07-23T00:00:00Z"))
                .stayDuration(8 * 60 * 60)
                .location(geometryFactory.createPoint(new Coordinate(30.5234, 50.4501)))
                .build();

        List<WeatherSampleCandidate> samples = policy.forStay(stay, WeatherTargetSource.HISTORICAL_BACKFILL, 10);

        assertThat(samples)
                .extracting(WeatherSampleCandidate::targetAt)
                .containsExactly(
                        Instant.parse("2026-07-23T01:00:00Z"),
                        Instant.parse("2026-07-23T03:00:00Z"),
                        Instant.parse("2026-07-23T05:00:00Z"),
                        Instant.parse("2026-07-23T07:00:00Z")
                );
    }

    @Test
    void longTripIsCappedAtEightSamples() {
        TimelineTripEntity trip = TimelineTripEntity.builder()
                .timestamp(Instant.parse("2026-07-23T00:00:00Z"))
                .tripDuration(24 * 60 * 60)
                .build();

        List<Instant> samples = policy.sampleTimesForTrip(trip);

        assertThat(samples).hasSize(8);
        assertThat(samples.getFirst()).isEqualTo(Instant.parse("2026-07-23T01:00:00Z"));
        assertThat(samples.getLast()).isEqualTo(Instant.parse("2026-07-23T22:00:00Z"));
    }

    @Test
    void ongoingSampleTimeUsesAtLeastThirtyMinuteIntervalAndDeduplicatesToHour() {
        Instant sample = policy.ongoingSampleTime(Instant.parse("2026-07-23T10:42:00Z"), 5);

        assertThat(sample).isEqualTo(Instant.parse("2026-07-23T10:00:00Z"));
    }
}
