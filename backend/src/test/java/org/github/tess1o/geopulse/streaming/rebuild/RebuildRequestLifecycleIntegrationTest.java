package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geocoding.service.GeocodingProviderFactory;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.streaming.model.TimelineRebuildRequest;
import org.github.tess1o.geopulse.streaming.service.RealtimeTimelineProcessor.Outcome;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.TimelineJobProgressService;
import org.github.tess1o.geopulse.streaming.service.TripStaySplitOverrideService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * How rebuild requests are written, consumed and protected: the guarantees the real-time job relies on to skip users
 * with nothing new without ever missing a change.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class RebuildRequestLifecycleIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    TimelineJobProgressService jobProgressService;

    @Inject
    StreamingTimelineGenerationService generationService;

    private GeocodingProviderFactory geocoding;

    @BeforeEach
    void setUp() {
        geocoding = GeocodingTestMocks.install();
    }

    @Test
    void savedPointsRequestARebuildFromTheEarliestOne() {
        UUID userId = harness.createUser("request-merge");
        Commute commute = new Commute();

        harness.ingest(userId, commute.office());
        TimelineRebuildRequest afterOffice = harness.pendingRebuild(userId).orElseThrow();
        assertThat(afterOffice.rebuildFrom()).isEqualTo(at("08:21"));

        harness.ingest(userId, commute.home());
        TimelineRebuildRequest afterHome = harness.pendingRebuild(userId).orElseThrow();
        assertThat(afterHome.rebuildFrom()).isEqualTo(at("06:00"));
        assertThat(afterHome.revision()).isGreaterThan(afterOffice.revision());

        // Duplicates are not saved, so they request nothing.
        harness.ingest(userId, commute.home());
        assertThat(harness.pendingRebuild(userId).orElseThrow().revision()).isEqualTo(afterHome.revision());
    }

    @Test
    void passWithARequestRebuildsAndClearsIt() {
        UUID userId = harness.createUser("request-consumed");
        harness.ingest(userId, new Commute().all());

        assertThat(harness.process(userId)).isEqualTo(Outcome.REBUILT);

        assertThat(harness.pendingRebuild(userId)).isEmpty();
        assertThat(harness.snapshot(userId).stays()).hasSize(2);
        assertThat(harness.snapshot(userId).trips()).hasSize(1);
        harness.assertMatchesFullRebuild(userId, "single rebuild");
    }

    @Test
    void passWithoutARequestOnlyExtendsTheOngoingGap() throws InterruptedException {
        UUID userId = harness.createUser("gap-only");
        harness.ingest(userId, new Commute().all());
        harness.process(userId);
        TimelineReplayHarness.TimelineSnapshot rebuilt = harness.snapshot(userId);
        Instant gapEnd = harness.ongoingGapEnd(userId);
        assertThat(rebuilt.gaps()).anySatisfy(gap -> assertThat(gap).endsWith("ongoing"));

        Thread.sleep(20);
        assertThat(harness.process(userId)).isEqualTo(Outcome.GAP_ONLY);

        TimelineReplayHarness.TimelineSnapshot idle = harness.snapshot(userId);
        assertThat(idle.stayIds()).isEqualTo(rebuilt.stayIds());
        assertThat(idle.content()).isEqualTo(rebuilt.content());
        assertThat(harness.ongoingGapEnd(userId)).isAfter(gapEnd);
    }

    @Test
    void userWithoutGpsDataGetsNothing() {
        UUID userId = harness.createUser("no-data");

        assertThat(harness.process(userId)).isEqualTo(Outcome.GAP_ONLY);

        assertThat(harness.snapshot(userId).content()).isEmpty();
    }

    @Test
    void pointSavedWhileAGenerationRunsIsNotLostAndIsNotBlocked() throws Exception {
        UUID userId = harness.createUser("concurrent");
        // Coordinates no other test uses: the generation must miss the shared geocoding cache and call the provider.
        Commute commute = new Commute(46.4825, 30.7233);
        harness.ingest(userId, commute.all());

        // Hold the generation inside its transaction: reverse geocoding the first stay waits for a signal.
        CountDownLatch insideGeneration = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(geocoding.reverseGeocode(any(Point.class))).thenAnswer(invocation -> {
            insideGeneration.countDown();
            release.await(60, TimeUnit.SECONDS);
            return Uni.createFrom().item(GeocodingTestMocks.result(invocation.getArgument(0)));
        });
        // Threads of the common pool lack the application's class loader, so use our own.
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<Outcome> generation = executor.submit(() -> harness.process(userId));
            assertThat(insideGeneration.await(60, TimeUnit.SECONDS)).as("generation reached geocoding").isTrue();

            // Saving a point must not wait for the generation's lock on the user.
            OwnTracksLocationMessage late =
                    Track.message(commute.home[0], commute.home[1] + 0.0001, at("07:02"), 10.0, 0.0);
            executor.submit(() -> harness.ingest(userId, List.of(late))).get(15, TimeUnit.SECONDS);
            // Another pass must not wait either: it skips the user.
            assertThat(executor.submit(() -> harness.process(userId)).get(15, TimeUnit.SECONDS))
                    .isEqualTo(Outcome.SKIPPED_BUSY);

            release.countDown();
            assertThat(generation.get(60, TimeUnit.SECONDS)).isEqualTo(Outcome.REBUILT);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }

        // The generation may have read the points before the late one was committed, so its request stays.
        TimelineRebuildRequest kept = harness.pendingRebuild(userId).orElseThrow();
        assertThat(kept.rebuildFrom()).isBeforeOrEqualTo(at("07:02"));

        GeocodingTestMocks.install();
        assertThat(harness.process(userId)).isEqualTo(Outcome.REBUILT);
        harness.assertMatchesFullRebuild(userId, "point saved during a generation");
    }

    @Test
    void userWithARunningTimelineJobIsSkipped() {
        UUID userId = harness.createUser("active-job");
        harness.ingest(userId, new Commute().all());
        UUID jobId = jobProgressService.createJob(userId);

        assertThat(harness.process(userId)).isEqualTo(Outcome.SKIPPED_ACTIVE_JOB);
        assertThat(harness.pendingRebuild(userId)).isPresent();

        jobProgressService.completeJob(jobId);
        assertThat(harness.process(userId)).isEqualTo(Outcome.REBUILT);
        assertThat(harness.pendingRebuild(userId)).isEmpty();
    }

    @Test
    void failingRebuildIsCountedThenParkedThenRetried() {
        UUID userId = harness.createUser("failing");
        Commute commute = new Commute();
        harness.ingest(userId, commute.home());
        harness.process(userId);

        AtomicBoolean failing = new AtomicBoolean(true);
        TripStaySplitOverrideService splits = mock(TripStaySplitOverrideService.class);
        doAnswer(invocation -> {
            if (failing.get()) {
                throw new IllegalStateException("simulated generation failure");
            }
            return 0;
        }).when(splits).reapplyManualOverrides(any(UUID.class));
        QuarkusMock.installMockForType(splits, TripStaySplitOverrideService.class);

        harness.ingest(userId, commute.driveAndOffice());
        for (int attempt = 1; attempt <= TimelineRebuildRequest.MAX_FAILED_ATTEMPTS; attempt++) {
            assertThatThrownBy(() -> harness.process(userId)).isInstanceOf(RuntimeException.class);
            TimelineRebuildRequest request = harness.pendingRebuild(userId).orElseThrow();
            // Counted although the generation's own transaction rolled back.
            assertThat(request.failedAttempts()).isEqualTo(attempt);
            assertThat(request.lastFailedAt()).isNotNull();
        }
        assertThat(harness.lastRebuildError(userId)).contains("simulated generation failure");

        // Parked: the pass keeps the latest stays updating without retrying the failing request.
        failing.set(false);
        assertThat(harness.process(userId)).isEqualTo(Outcome.REBUILT_LATEST_ONLY);
        assertThat(harness.pendingRebuild(userId).orElseThrow().failedAttempts())
                .isEqualTo(TimelineRebuildRequest.MAX_FAILED_ATTEMPTS);

        // Once the retry delay has passed, the request is tried again and cleared.
        harness.backdateLastFailure(userId, TimelineRebuildRequest.RETRY_FAILED_AFTER.plusMinutes(1));
        assertThat(harness.process(userId)).isEqualTo(Outcome.REBUILT);
        assertThat(harness.pendingRebuild(userId)).isEmpty();
        harness.assertMatchesFullRebuild(userId, "after a parked request was retried");
    }

    @Test
    void anyGenerationCoversThePendingRequest() {
        UUID userId = harness.createUser("covered");
        Commute commute = new Commute();
        harness.ingest(userId, commute.driveAndOffice());
        harness.process(userId);
        harness.ingest(userId, commute.home());
        assertThat(harness.pendingRebuild(userId).orElseThrow().rebuildFrom()).isEqualTo(at("06:00"));

        // A generation started for another reason, from a later time, still covers the earlier request.
        generationService.generateTimelineFromTimestamp(userId, at("11:00"), null, "campaign");

        assertThat(harness.pendingRebuild(userId)).isEmpty();
        assertThat(harness.snapshot(userId).stays().getFirst()).contains(at("06:00").toString());
        harness.assertMatchesFullRebuild(userId, "request covered by a campaign generation");
    }

    private static Instant at(String time) {
        return Instant.parse("2026-09-07T" + time + ":00Z");
    }

    /** Home 06:00-08:00, drive 08:01-08:20, office 08:21-12:00, in a coordinate scope of its own. */
    private static final class Commute {
        private final Track track = new Track();
        final double[] home;
        final double[] office;

        Commute() {
            this(49.8420, 24.0310);
        }

        Commute(double homeLat, double homeLon) {
            home = track.place(homeLat, homeLon);
            office = track.place(homeLat - 0.0070, homeLon + 0.0440);
        }

        List<OwnTracksLocationMessage> home() {
            return new Track().stay(home, at("06:00"), at("08:00"), 5).messages();
        }

        List<OwnTracksLocationMessage> driveAndOffice() {
            return new Track()
                    .drive(home, office, at("08:01"), at("08:20"))
                    .stay(office, at("08:21"), at("12:00"), 10)
                    .messages();
        }

        List<OwnTracksLocationMessage> office() {
            return new Track().stay(office, at("08:21"), at("12:00"), 10).messages();
        }

        List<OwnTracksLocationMessage> all() {
            return new Track()
                    .stay(home, at("06:00"), at("08:00"), 5)
                    .drive(home, office, at("08:01"), at("08:20"))
                    .stay(office, at("08:21"), at("12:00"), 10)
                    .messages();
        }
    }
}
