package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripMovementOverrideEntity;
import org.github.tess1o.geopulse.streaming.model.shared.MovementTypeSource;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripMovementOverrideRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.streaming.rebuild.TimelineReplayHarness.ISSUE_631_EXPORT;
import static org.github.tess1o.geopulse.streaming.rebuild.TimelineReplayHarness.readResource;

/**
 * Replays the day attached to GitHub issue #631. The user marked three trips as BOAT, then a newer timeline engine
 * regenerated the day. The new trips cover the same times, but each starts on an earlier point 500-900 m away from
 * where the corrected trip started, so the corrections must still find them.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class ManualMovementTypeRegenerationIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    TimelineTripMovementOverrideRepository overrideRepository;

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void correctionsMadeOnTheOlderEngineStillApplyAfterRegeneration() {
        UUID userId = harness.importExport(readResource(ISSUE_631_EXPORT), "issue-631");

        List<String> described = harness.snapshot(userId).trips();

        // The corrected trips started at 07:21:12, 11:29:02 and 12:22:21; the regenerated ones start a few minutes
        // earlier on other points
        for (String during : List.of("2026-05-03T08:30:00Z", "2026-05-03T11:30:00Z", "2026-05-03T13:00:00Z")) {
            TimelineTripEntity trip = tripAt(userId, Instant.parse(during));
            assertThat(trip).as("trip at %s in %s", during, described).isNotNull();
            assertThat(trip.getMovementType()).as("trip at %s in %s", during, described).isEqualTo("BOAT");
            assertThat(trip.getMovementTypeSource()).as("trip at %s", during).isEqualTo(MovementTypeSource.MANUAL);
        }
        assertThat(overrides(userId)).hasSize(3).allSatisfy(override -> assertThat(override.getTrip()).isNotNull());
    }

    private TimelineTripEntity tripAt(UUID userId, Instant time) {
        return QuarkusTransaction.requiringNew().call(() -> tripRepository.findByUser(userId).stream()
                .filter(trip -> !trip.getTimestamp().isAfter(time)
                        && trip.getTimestamp().plusSeconds(trip.getTripDuration()).isAfter(time))
                .findFirst()
                .orElse(null));
    }

    private List<TimelineTripMovementOverrideEntity> overrides(UUID userId) {
        return QuarkusTransaction.requiringNew().call(() -> overrideRepository.findByUserId(userId));
    }
}
