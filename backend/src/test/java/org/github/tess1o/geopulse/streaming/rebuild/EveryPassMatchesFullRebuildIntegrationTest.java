package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.streaming.rebuild.TimelineReplayHarness.ISSUE_627_EXPORT;
import static org.github.tess1o.geopulse.streaming.rebuild.TimelineReplayHarness.uploadBatches;

/**
 * What a user sees at any moment, not only at the end of the day: after every real-time pass, the timeline must equal
 * a full rebuild of exactly the points stored so far. A twin user receives the same points and is fully rebuilt each
 * time. This also catches a timeline that changes back and forth between passes.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class EveryPassMatchesFullRebuildIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @ParameterizedTest(name = "uploads every {0} minutes")
    @ValueSource(ints = {15, 60})
    void realDay(int minutes) {
        UUID live = harness.createUserWithSettingsOf(ISSUE_627_EXPORT, "every-pass-live-" + minutes);
        UUID twin = harness.createUserWithSettingsOf(ISSUE_627_EXPORT, "every-pass-twin-" + minutes);

        replay(live, twin, uploadBatches(harness.messages(ISSUE_627_EXPORT), minutes));
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {61, 62, 63})
    void generatedDays(long seed) {
        UUID live = harness.createUserWithFavoriteAreas("every-pass-live-" + seed, SyntheticDays.NAMED_PLACES);
        UUID twin = harness.createUserWithFavoriteAreas("every-pass-twin-" + seed, SyntheticDays.NAMED_PLACES);

        replay(live, twin, uploadBatches(SyntheticDays.generate(seed, LocalDate.of(2026, 9, 3), 4), 60));
    }

    private void replay(UUID live, UUID twin, List<List<OwnTracksLocationMessage>> batches) {
        for (List<OwnTracksLocationMessage> batch : batches) {
            harness.ingest(live, batch);
            harness.process(live);
            harness.ingest(twin, batch);
            harness.fullRebuild(twin);

            Instant last = Instant.ofEpochSecond(batch.getLast().getTst());
            List<String> realtime = harness.snapshot(live).contentAcrossUsers();
            List<String> full = harness.snapshot(twin).contentAcrossUsers();
            assertThat(realtime)
                    .as("after the upload ending %s%nreal-time:%n  %s%nfull rebuild:%n  %s", last,
                            String.join("\n  ", realtime), String.join("\n  ", full))
                    .containsExactlyElementsOf(full);
        }
    }
}
