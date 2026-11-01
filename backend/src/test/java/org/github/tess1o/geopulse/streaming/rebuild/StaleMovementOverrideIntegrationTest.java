package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.shared.MovementTypeSource;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.service.TripMovementTypeOverrideService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A movement type belongs to the trip the user corrected. When that trip's points are replaced by another trip at the
 * same time but somewhere else, the old correction must not follow the time and repaint the unrelated trip.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class StaleMovementOverrideIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    ManualEditsSupport edits;

    @Inject
    TripMovementTypeOverrideService movementTypeOverrideService;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    GpsPointService gpsPointService;

    private final Track original = new Track();
    private final double[] home = original.place(49.8420, 24.0310);
    private final double[] office = original.place(49.8350, 24.0750);

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void aCorrectionDoesNotMoveToAnUnrelatedTripReplacingTheCorrectedOne() {
        UUID userId = harness.createUser("stale-movement");
        harness.ingest(userId, original
                .stay(home, at("06:00"), at("08:00"), 10)
                .drive(home, office, at("08:01"), at("08:20"))
                .stay(office, at("08:21"), at("10:00"), 10)
                .messages());
        harness.fullRebuild(userId);
        TimelineTripEntity corrected = edits.tripCovering(userId, at("08:05"), at("08:15"));
        movementTypeOverrideService.setManualMovementType(userId, corrected.getId(), TripType.TRAIN);

        // The morning's points are replaced by another drive at the same time, 10 km away.
        List<Long> morning = harness.pointIds(userId, at("08:00"), at("08:21"));
        QuarkusTransaction.requiringNew().run(() -> gpsPointService.deleteGpsPoints(morning, userId));
        Track elsewhere = new Track();
        harness.ingest(userId, elsewhere
                .drive(elsewhere.place(49.9000, 24.3000), elsewhere.place(49.9300, 24.3500), at("08:01"), at("08:20"))
                .messages());
        harness.fullRebuild(userId);

        List<TimelineTripEntity> trips = QuarkusTransaction.requiringNew().call(() -> tripRepository
                .find("user.id = ?1 order by timestamp", userId).list());
        assertThat(trips).as("the unrelated drive replacing the corrected one").hasSize(1);
        TimelineTripEntity replacement = trips.getFirst();
        assertThat(replacement.getMovementType())
                .as("the replaced trip's movement type")
                .isNotEqualTo(TripType.TRAIN.name());
        assertThat(replacement.getMovementTypeSource())
                .as("the replaced trip is detected, not corrected")
                .isNotEqualTo(MovementTypeSource.MANUAL);
    }

    private static Instant at(String time) {
        return Instant.parse("2026-09-16T" + time + ":00Z");
    }
}
