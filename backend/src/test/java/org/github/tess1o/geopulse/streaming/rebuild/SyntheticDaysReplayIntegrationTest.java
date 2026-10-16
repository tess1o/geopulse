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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.streaming.rebuild.TimelineReplayHarness.uploadBatches;

/**
 * The same property as {@link RealDayReplayIntegrationTest} over generated multi-day recordings (Thursday to Sunday,
 * so weekend routines are included): however the points arrive, the real-time timeline must equal a full rebuild.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class SyntheticDaysReplayIntegrationTest {

    private static final LocalDate THURSDAY = LocalDate.of(2026, 9, 3);
    private static final int DAYS = 4;

    @Inject
    TimelineReplayHarness harness;

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {11, 12, 13, 14, 15, 16, 17, 18, 19, 20})
    void liveUploadsMatchFullRebuild(long seed) {
        List<OwnTracksLocationMessage> days = SyntheticDays.generate(seed, THURSDAY, DAYS);
        UUID userId = newUser("live-" + seed);

        harness.replayLive(userId, uploadBatches(days, 5));

        harness.assertMatchesFullRebuild(userId, "live 5-minute uploads, seed " + seed);
        assertThat(harness.snapshot(userId).stays()).as("the generated days produce a real timeline").hasSizeGreaterThan(8);
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {21, 22, 23, 24, 25, 26, 27, 28, 29, 30})
    void windowsDeliveredLate(long seed) {
        List<OwnTracksLocationMessage> days = SyntheticDays.generate(seed, THURSDAY, DAYS);
        Random random = new Random(seed);
        UUID userId = newUser("late-" + seed);

        // Each upload of 5 minutes has a one-in-eight chance of being held back by a device that is offline, and
        // held uploads arrive together some uploads later.
        List<List<OwnTracksLocationMessage>> held = new ArrayList<>();
        for (List<OwnTracksLocationMessage> batch : uploadBatches(days, 5)) {
            if (random.nextInt(8) == 0) {
                held.add(batch);
                continue;
            }
            harness.ingest(userId, batch);
            if (!held.isEmpty() && random.nextInt(10) == 0) {
                held.forEach(late -> harness.ingest(userId, late));
                held.clear();
            }
            harness.process(userId);
        }
        held.forEach(late -> harness.ingest(userId, late));
        harness.process(userId);

        harness.assertMatchesFullRebuild(userId, "late windows, seed " + seed);
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {31, 32, 33, 34, 35})
    void wholeDayUploadedNextMorning(long seed) {
        List<OwnTracksLocationMessage> days = SyntheticDays.generate(seed, THURSDAY, DAYS);
        UUID userId = newUser("next-morning-" + seed);
        long fridayStart = THURSDAY.plusDays(1).atStartOfDay(SyntheticDays.ZONE).toEpochSecond();
        long saturdayStart = THURSDAY.plusDays(2).atStartOfDay(SyntheticDays.ZONE).toEpochSecond();

        // The phone was offline on Friday; Saturday's live uploads arrive first, then Friday in one batch.
        List<OwnTracksLocationMessage> friday = days.stream()
                .filter(m -> m.getTst() >= fridayStart && m.getTst() < saturdayStart).toList();
        List<OwnTracksLocationMessage> rest = days.stream()
                .filter(m -> m.getTst() < fridayStart || m.getTst() >= saturdayStart).toList();
        harness.replayLive(userId, uploadBatches(rest, 5));
        harness.ingest(userId, friday);
        harness.process(userId);

        harness.assertMatchesFullRebuild(userId, "Friday uploaded after Saturday, seed " + seed);
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {41, 42, 43, 44, 45})
    void uploadsInRandomOrder(long seed) {
        List<OwnTracksLocationMessage> days = SyntheticDays.generate(seed, THURSDAY, DAYS);
        UUID userId = newUser("shuffled-" + seed);
        List<List<OwnTracksLocationMessage>> batches = new ArrayList<>(uploadBatches(days, 60));
        Collections.shuffle(batches, new Random(seed));

        harness.replayLive(userId, batches);

        harness.assertMatchesFullRebuild(userId, "hourly uploads in random order, seed " + seed);
    }

    private UUID newUser(String name) {
        return harness.createUserWithFavoriteAreas("synthetic-" + name, SyntheticDays.NAMED_PLACES);
    }
}
