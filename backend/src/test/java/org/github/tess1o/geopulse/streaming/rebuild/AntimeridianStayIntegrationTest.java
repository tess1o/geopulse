package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.engine.StreamingTimelineProcessor;
import org.github.tess1o.geopulse.streaming.iterator.StreamingGpsIterable;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.service.explanation.TimelineEventExplanationService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Places on the 180th meridian (Fiji, Chukotka, Kiribati): a stay whose points lie on both sides of it is at the
 * meridian, not at the arithmetic mean of +180 and -180, which is on the other side of the world.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class AntimeridianStayIntegrationTest {

    /** Taveuni, Fiji, where the meridian crosses land. */
    private static final double LATITUDE = -16.80;
    /** 32 m east and west of the meridian at that latitude. */
    private static final double EAST = 179.9997;
    private static final double WEST = -179.9997;

    @Inject
    TimelineReplayHarness harness;

    @Inject
    TimelineStayRepository stays;

    @Inject
    GpsPointRepository gpsPoints;

    @Inject
    TimelineConfigurationProvider configuration;

    @Inject
    StreamingTimelineProcessor processor;

    @Inject
    TimelineEventExplanationService explanations;

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void stayAcrossTheMeridianIsOneStayOnIt() {
        UUID userId = harness.createUser("antimeridian-stay");
        List<OwnTracksLocationMessage> messages = new ArrayList<>();
        Instant time = at("06:00");
        for (int i = 0; time.isBefore(at("08:00")); i++, time = time.plus(Duration.ofMinutes(5))) {
            messages.add(Track.message(LATITUDE, i % 2 == 0 ? EAST : WEST, time, 10.0, 0.0));
        }
        // Then away, 1 km west of the meridian, to end the stay
        for (int i = 1; i <= 10; i++) {
            messages.add(Track.message(LATITUDE, 180.0 - 0.001 * i, at("08:00").plus(Duration.ofMinutes(i)), 8.0, 40.0));
        }
        harness.ingest(userId, messages);
        harness.fullRebuild(userId);

        List<TimelineStayEntity> stored = QuarkusTransaction.requiringNew().call(() ->
                stays.find("user.id = ?1 order by timestamp", userId).list());
        assertThat(stored).first().satisfies(stay -> {
            assertThat(stay.getTimestamp()).isEqualTo(at("06:00"));
            assertThat(stay.getStayDuration()).isGreaterThanOrEqualTo(Duration.ofMinutes(115).toSeconds());
            assertThat(Math.abs(stay.getLocation().getX())).isGreaterThan(179.999);
            assertThat(stay.getLocation().getY()).isEqualTo(LATITUDE);
        });

        // Its explanation measures the points from the same centre the engine used: all of them within the radius
        var explained = explanations.explainStay(stored.getFirst().getId(), userId).orElseThrow();
        assertThat(Math.abs(explained.centre().longitude())).isGreaterThan(179.999);
        assertThat(explained.points()).filteredOn(point -> point.part().equals("EVENT")).isNotEmpty()
                .allSatisfy(point -> {
                    assertThat(point.outsideRadius()).isFalse();
                    assertThat(point.distanceFromCentreMeters()).isLessThan(50.0);
                });
    }

    @Test
    void stayInferredAcrossAGapOnTheMeridianIsOnIt() {
        UUID userId = harness.createUser("antimeridian-gap");
        List<OwnTracksLocationMessage> messages = new ArrayList<>();
        // Driving east towards the meridian, then nothing for four hours, then 425 m further on its other side
        for (int i = 0; i < 5; i++) {
            messages.add(Track.message(LATITUDE, 179.990 + 0.002 * i, at("08:00").plus(Duration.ofMinutes(i)), 8.0, 25.0));
        }
        for (int i = 0; i < 5; i++) {
            messages.add(Track.message(LATITUDE, -179.998 + 0.002 * i, at("12:04").plus(Duration.ofMinutes(i)), 8.0, 25.0));
        }
        harness.ingest(userId, messages);
        var config = configuration.getConfigurationForUser(userId);
        config.setGapStayInferenceEnabled(true);

        List<Stay> inferred = QuarkusTransaction.requiringNew().call(() -> processor.processPoints(
                        new StreamingGpsIterable(gpsPoints, userId, Instant.EPOCH, 10_000), config, userId)).stream()
                .filter(Stay.class::isInstance).map(Stay.class::cast).toList();

        assertThat(inferred).singleElement().satisfies(stay -> {
            assertThat(stay.getStartTime()).isEqualTo(at("08:04"));
            assertThat(Math.abs(stay.getLongitude())).isGreaterThan(179.99);
        });
    }

    private static Instant at(String time) {
        return Instant.parse("2026-09-08T" + time + ":00Z");
    }
}
