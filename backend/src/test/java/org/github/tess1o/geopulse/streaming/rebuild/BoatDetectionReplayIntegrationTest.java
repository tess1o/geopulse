package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.shared.MovementTypeSource;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.streaming.rebuild.TimelineReplayHarness.ISSUE_631_EXPORT;
import static org.github.tess1o.geopulse.streaming.rebuild.TimelineReplayHarness.readResource;
import static org.github.tess1o.geopulse.streaming.rebuild.TimelineReplayHarness.withoutOverrides;

/**
 * Replays the day attached to GitHub issue #631 with boat detection on and the user's water flags, without the
 * user's manual corrections. On the way back the boat's GPS went quiet for 20 minutes; the only point in between is a
 * network fix on the shore, and both 3 km stretches to it were counted as land, leaving the trip short of the water
 * ratio a boat needs.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class BoatDetectionReplayIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    TimelineTripRepository tripRepository;

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
        harness.installWaterDataset();
    }

    @AfterEach
    void tearDown() {
        harness.removeWaterDataset();
    }

    @Test
    void boatTripsAreDetectedWithoutManualCorrections() {
        UUID userId = harness.importExportWithWater(withoutOverrides(readResource(ISSUE_631_EXPORT)), "issue-631-boat");

        List<String> described = harness.snapshot(userId).trips();

        // The trip out (07:46-09:36) and the trip back (12:20-14:52)
        for (String during : List.of("2026-05-03T08:30:00Z", "2026-05-03T13:00:00Z")) {
            TimelineTripEntity trip = tripAt(userId, Instant.parse(during));
            assertThat(trip).as("trip at %s in %s", during, described).isNotNull();
            assertThat(trip.getMovementType()).as("trip at %s in %s", during, described).isEqualTo("BOAT");
            assertThat(trip.getMovementTypeSource()).as("trip at %s", during).isEqualTo(MovementTypeSource.AUTO);
        }
    }

    private TimelineTripEntity tripAt(UUID userId, Instant time) {
        return QuarkusTransaction.requiringNew().call(() -> tripRepository.findByUser(userId).stream()
                .filter(trip -> !trip.getTimestamp().isAfter(time)
                        && trip.getTimestamp().plusSeconds(trip.getTripDuration()).isAfter(time))
                .findFirst()
                .orElse(null));
    }
}
