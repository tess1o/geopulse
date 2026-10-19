package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.streaming.rebuild.TimelineReplayHarness.ISSUE_632_EXPORT;
import static org.github.tess1o.geopulse.streaming.rebuild.TimelineReplayHarness.readResource;
import static org.github.tess1o.geopulse.streaming.rebuild.TimelineReplayHarness.uploadBatches;

/**
 * Replays the day attached to GitHub issue #632. The phone sent only a few points while stopped, and each of them
 * reported a driving speed, so the stop never showed as slow points and the whole day after leaving home became one
 * trip. The stops must be found from the points' times and places instead, the same way whatever the delivery order.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class SparseStopReplayIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    private List<OwnTracksLocationMessage> day;

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
        day = harness.messages(ISSUE_632_EXPORT);
        assertThat(day).hasSize(163);
    }

    @Test
    void fullRebuildEndsEachDriveWhereItStopped() {
        UUID userId = harness.importExport(readResource(ISSUE_632_EXPORT), "sparse-stop");

        TimelineReplayHarness.TimelineSnapshot timeline = harness.snapshot(userId);
        String described = String.join("\n  ", timeline.content());

        // The destination: the last point of the drive at 07:34:47, the next one 15 minutes later about 100 m away
        assertThat(timeline.stays()).as(described)
                .anyMatch(stay -> stay.startsWith("STAY 2026-10-17T07:34:47Z") && stay.contains("SPARSE_STOP"));
        // Home: the last point of the drive back at 09:56:13, the next one 16 minutes later about 200 m away
        assertThat(timeline.stays()).as(described)
                .anyMatch(stay -> stay.startsWith("STAY 2026-10-17T09:56:13Z") && stay.contains("SPARSE_STOP"));
        // A drive there and a drive back, instead of one trip from the morning to the end of the day
        assertThat(timeline.trips()).as(described)
                .filteredOn(trip -> trip.startsWith("TRIP 2026-10-17"))
                .hasSize(2);
    }

    @ParameterizedTest(name = "uploads every {0} minutes")
    @ValueSource(ints = {1, 5, 15, 60})
    void liveUploadsMatchFullRebuild(int minutes) {
        UUID userId = newUser("live-" + minutes);

        harness.replayLive(userId, uploadBatches(day, minutes));

        harness.assertMatchesFullRebuild(userId, "uploads every " + minutes + " minutes");
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1, 2, 3})
    void uploadsInRandomOrder(long seed) {
        UUID userId = newUser("random-" + seed);
        List<List<OwnTracksLocationMessage>> batches = new ArrayList<>(uploadBatches(day, 5));
        Collections.shuffle(batches, new Random(seed));

        harness.replayLive(userId, batches);

        harness.assertMatchesFullRebuild(userId, "5-minute uploads in random order, seed " + seed);
    }

    private UUID newUser(String name) {
        return harness.createUserWithSettingsOf(ISSUE_632_EXPORT, "sparse-stop-" + name);
    }
}
