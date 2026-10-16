package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.streaming.service.RealtimeTimelineProcessor.Outcome;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.streaming.rebuild.TimelineReplayHarness.ISSUE_627_EXPORT;
import static org.github.tess1o.geopulse.streaming.rebuild.TimelineReplayHarness.uploadBatches;

/**
 * Replays a real recorded day (GitHub issue #627) through live ingest under different delivery patterns. Whatever the
 * order, the real-time timeline must end up exactly as a clean full rebuild of the same points.
 * <p>
 * Before rebuild requests, the real-time job rebuilt only from the latest stay and this day ended with a 2.7-hour
 * "walk" of 40 m between two stays at home that a full rebuild merges into one stay.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class RealDayReplayIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    private List<OwnTracksLocationMessage> day;

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
        day = harness.messages(ISSUE_627_EXPORT);
        assertThat(day).hasSize(246);
    }

    @ParameterizedTest(name = "uploads every {0} minutes")
    @ValueSource(ints = {1, 5, 15, 60})
    void liveUploadsMatchFullRebuild(int minutes) {
        UUID userId = newUser("live-" + minutes);

        harness.replayLive(userId, uploadBatches(day, minutes));

        harness.assertMatchesFullRebuild(userId, "uploads every " + minutes + " minutes");
    }

    @Test
    void passesWithoutNewDataLeaveTheTimelineUntouched() {
        UUID userId = newUser("idle");

        for (List<OwnTracksLocationMessage> batch : uploadBatches(day, 15)) {
            harness.ingest(userId, batch);
            assertThat(harness.process(userId)).isEqualTo(Outcome.REBUILT);

            TimelineReplayHarness.TimelineSnapshot afterRebuild = harness.snapshot(userId);
            assertThat(harness.process(userId)).isEqualTo(Outcome.GAP_ONLY);
            assertThat(harness.process(userId)).isEqualTo(Outcome.GAP_ONLY);
            TimelineReplayHarness.TimelineSnapshot afterIdlePasses = harness.snapshot(userId);
            // Same stays with the same ids: nothing was deleted and recreated.
            assertThat(afterIdlePasses.stayIds()).isEqualTo(afterRebuild.stayIds());
            assertThat(afterIdlePasses.content()).isEqualTo(afterRebuild.content());
        }

        harness.assertMatchesFullRebuild(userId, "idle passes between uploads");
    }

    @Test
    void resentUploadsChangeNothing() {
        UUID userId = newUser("resent");
        List<List<OwnTracksLocationMessage>> batches = uploadBatches(day, 5);
        harness.replayLive(userId, batches);
        TimelineReplayHarness.TimelineSnapshot before = harness.snapshot(userId);

        // A client retrying after a lost response sends everything again: all duplicates, nothing to rebuild.
        for (List<OwnTracksLocationMessage> batch : batches) {
            harness.ingest(userId, batch);
            assertThat(harness.process(userId)).isEqualTo(Outcome.GAP_ONLY);
        }

        TimelineReplayHarness.TimelineSnapshot after = harness.snapshot(userId);
        assertThat(after.stayIds()).isEqualTo(before.stayIds());
        harness.assertMatchesFullRebuild(userId, "resent uploads");
    }

    @Test
    void morningDriveArrivingAtTheEndOfTheDay() {
        UUID userId = newUser("late-drive");
        Instant from = Instant.parse("2026-10-08T05:30:00Z");
        Instant to = Instant.parse("2026-10-08T06:30:00Z");
        List<OwnTracksLocationMessage> held = new ArrayList<>();
        List<OwnTracksLocationMessage> live = new ArrayList<>();
        for (OwnTracksLocationMessage message : day) {
            Instant time = Instant.ofEpochSecond(message.getTst());
            (time.isBefore(from) || !time.isBefore(to) ? live : held).add(message);
        }
        assertThat(held).isNotEmpty();

        // A second device that was offline all day uploads its part of the morning in the evening.
        harness.replayLive(userId, uploadBatches(live, 5));
        harness.ingest(userId, held);
        assertThat(harness.process(userId)).isEqualTo(Outcome.REBUILT);

        harness.assertMatchesFullRebuild(userId, "morning drive delivered at the end of the day");
    }

    @Test
    void firstHalfBackfilledAfterTheSecondHalf() {
        UUID userId = newUser("backfill");
        List<OwnTracksLocationMessage> firstHalf = day.subList(0, day.size() / 2);
        List<OwnTracksLocationMessage> secondHalf = day.subList(day.size() / 2, day.size());

        // Recorder history sent after the integration was set up: the newer half is already processed.
        harness.replayLive(userId, uploadBatches(secondHalf, 5));
        harness.ingest(userId, firstHalf);
        assertThat(harness.process(userId)).isEqualTo(Outcome.REBUILT);

        harness.assertMatchesFullRebuild(userId, "first half backfilled after the second");
    }

    @Test
    void newestFirstInSmallBatches() {
        UUID userId = newUser("newest-first");
        List<List<OwnTracksLocationMessage>> batches = new ArrayList<>();
        for (int end = day.size(); end > 0; end -= 25) {
            batches.add(day.subList(Math.max(0, end - 25), end));
        }

        // A client draining its queue from the newest end.
        harness.replayLive(userId, batches);

        harness.assertMatchesFullRebuild(userId, "newest first in batches of 25");
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1, 2, 3, 4, 5})
    void uploadsInRandomOrder(long seed) {
        UUID userId = newUser("random-" + seed);
        List<List<OwnTracksLocationMessage>> batches = new ArrayList<>(uploadBatches(day, 5));
        Collections.shuffle(batches, new Random(seed));

        harness.replayLive(userId, batches);

        harness.assertMatchesFullRebuild(userId, "5-minute uploads in random order, seed " + seed);
    }

    @Test
    void severalUploadsBetweenPasses() {
        UUID userId = newUser("bursts");
        List<List<OwnTracksLocationMessage>> batches = uploadBatches(day, 5);

        // Points from several requests arrive between two passes of the job.
        for (int i = 0; i < batches.size(); i++) {
            harness.ingest(userId, batches.get(i));
            if (i % 7 == 6) {
                harness.process(userId);
            }
        }
        harness.process(userId);

        harness.assertMatchesFullRebuild(userId, "several uploads between passes");
    }

    private UUID newUser(String name) {
        return harness.createUserWithSettingsOf(ISSUE_627_EXPORT, "replay-" + name);
    }
}
