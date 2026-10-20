package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.favorites.model.AddAreaToFavoritesDto;
import org.github.tess1o.geopulse.favorites.service.FavoriteLocationService;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.TimelineImportHelper;
import org.github.tess1o.geopulse.streaming.model.TimelineJobProgress;
import org.github.tess1o.geopulse.streaming.service.RealtimeTimelineProcessor.Outcome;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.TimelineJobProgressService;
import org.github.tess1o.geopulse.streaming.service.TripStaySplitOverrideService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Every change that alters a timeline without saving a point must still leave a rebuild request behind, so the
 * real-time job catches up even when the regeneration it normally triggers is lost, blocked or fails.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class RebuildTriggersIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    GpsPointService gpsPointService;

    @Inject
    FavoriteLocationService favoriteLocationService;

    @Inject
    TimelineJobProgressService jobProgressService;

    @Inject
    TimelineImportHelper timelineImportHelper;

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void deletedPointsAreRemovedFromTheTimelineEvenIfTheScheduledRegenerationIsLost() {
        UUID userId = harness.createUser("delete-points");
        Day day = new Day();
        harness.ingest(userId, day.all());
        harness.process(userId);
        assertThat(harness.snapshot(userId).stays()).hasSize(3);

        // Delete the evening at the gym through the service only: the REST resource would also schedule an
        // in-memory regeneration, which a restart could lose.
        gpsPointService.deleteGpsPoints(harness.pointIds(userId, at("17:00"), at("20:00")), userId);
        assertThat(harness.pendingRebuild(userId).orElseThrow().rebuildFrom()).isEqualTo(at("17:00"));

        assertThat(harness.process(userId)).isEqualTo(Outcome.REBUILT);
        assertThat(harness.snapshot(userId).stays()).hasSize(2);
        harness.assertMatchesFullRebuild(userId, "after deleting points");
    }

    @Test
    void deletingASinglePointRequestsARebuild() {
        UUID userId = harness.createUser("delete-point");
        harness.ingest(userId, new Day().all());
        harness.process(userId);
        // Office points are at 08:21 plus whole multiples of ten minutes.
        Long pointId = harness.pointIds(userId, at("10:01"), at("10:02")).getFirst();

        gpsPointService.deleteGpsPoint(pointId, userId);

        assertThat(harness.pendingRebuild(userId).orElseThrow().rebuildFrom()).isEqualTo(at("10:01"));
        assertThat(harness.process(userId)).isEqualTo(Outcome.REBUILT);
        harness.assertMatchesFullRebuild(userId, "after deleting one point");
    }

    @Test
    void deletingAllGpsDataLeavesNothingToRebuild() {
        UUID userId = harness.createUser("delete-all");
        harness.ingest(userId, new Day().all());
        assertThat(harness.pendingRebuild(userId)).isPresent();

        gpsPointService.deleteAllGpsData(userId);

        assertThat(harness.pendingRebuild(userId)).isEmpty();
        assertThat(harness.process(userId)).isEqualTo(Outcome.GAP_ONLY);
        assertThat(harness.snapshot(userId).content()).isEmpty();
    }

    @Test
    void favoriteAddedWhileAnotherJobRunsIsAppliedByTheRealtimeJob() {
        UUID userId = harness.createUser("favorite-busy");
        Day day = new Day();
        harness.ingest(userId, day.all());
        harness.process(userId);
        UUID otherJob = jobProgressService.createJob(userId);

        favoriteLocationService.addFavorite(userId, area("Gym", day.gym));
        // Before rebuild requests this regeneration was only logged as "could not create" and never happened.
        // The caller gets the running job to follow; the regeneration itself waits for the request below.
        assertThat(favoriteLocationService.createTimelineRegenerationJob(userId)).isEqualTo(otherJob);
        assertThat(harness.pendingRebuild(userId).orElseThrow().rebuildFrom())
                .isEqualTo(StreamingTimelineGenerationService.DEFAULT_START_DATE);

        jobProgressService.completeJob(otherJob);
        assertThat(harness.process(userId)).isEqualTo(Outcome.REBUILT);

        assertThat(harness.snapshot(userId).stays()).anySatisfy(stay -> assertThat(stay).contains("'Gym'"));
        harness.assertMatchesFullRebuild(userId, "favorite added during another job");
    }

    @Test
    void favoriteRegenerationJobClearsItsRequest() throws InterruptedException {
        UUID userId = harness.createUser("favorite-job");
        Day day = new Day();
        harness.ingest(userId, day.all());
        harness.process(userId);

        favoriteLocationService.addFavorite(userId, area("Gym", day.gym));
        UUID jobId = favoriteLocationService.createTimelineRegenerationJob(userId);
        assertThat(jobId).isNotNull();
        awaitJob(jobId);

        assertThat(harness.pendingRebuild(userId)).isEmpty();
        assertThat(harness.snapshot(userId).stays()).anySatisfy(stay -> assertThat(stay).contains("'Gym'"));
        assertThat(harness.process(userId)).isEqualTo(Outcome.GAP_ONLY);
    }

    @Test
    void failedImportGenerationIsRetriedByTheRealtimeJob() {
        UUID userId = harness.createUser("import-failure");
        Day day = new Day();
        harness.ingest(userId, day.all());
        harness.process(userId);

        AtomicBoolean failing = new AtomicBoolean(true);
        TripStaySplitOverrideService splits = mock(TripStaySplitOverrideService.class);
        doAnswer(invocation -> {
            if (failing.get()) {
                throw new IllegalStateException("simulated import generation failure");
            }
            return 0;
        }).when(splits).reapplyManualOverrides(any(UUID.class));
        QuarkusMock.installMockForType(splits, TripStaySplitOverrideService.class);

        ImportJob job = new ImportJob(userId, new ImportOptions(), "history.json", new byte[0]);
        assertThatThrownBy(() -> timelineImportHelper.triggerTimelineGenerationForImportedGpsData(job, at("06:00")))
                .isInstanceOf(RuntimeException.class);

        // The imported points are committed; the request makes the real-time job finish the timeline.
        assertThat(harness.pendingRebuild(userId).orElseThrow().rebuildFrom()).isEqualTo(at("06:00"));
        failing.set(false);
        assertThat(harness.process(userId)).isEqualTo(Outcome.REBUILT);
        assertThat(harness.pendingRebuild(userId)).isEmpty();
        harness.assertMatchesFullRebuild(userId, "after a failed import generation");
    }

    @Test
    void successfulImportGenerationClearsItsRequest() {
        UUID userId = harness.createUser("import-success");
        harness.ingest(userId, new Day().all());

        ImportJob job = new ImportJob(userId, new ImportOptions(), "history.json", new byte[0]);
        UUID timelineJobId = timelineImportHelper.triggerTimelineGenerationForImportedGpsData(job, at("06:00"));
        // As the import strategies do once the generation returns.
        timelineImportHelper.finishTimelineJob(timelineJobId, job);

        assertThat(harness.pendingRebuild(userId)).isEmpty();
        assertThat(harness.process(userId)).isEqualTo(Outcome.GAP_ONLY);
        harness.assertMatchesFullRebuild(userId, "after an import");
    }

    private void awaitJob(UUID jobId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (Instant.now().isBefore(deadline)) {
            TimelineJobProgress progress = jobProgressService.getJobProgress(jobId).orElseThrow();
            if (progress.isTerminal()) {
                assertThat(progress.getStatus()).isEqualTo(TimelineJobProgress.JobStatus.COMPLETED);
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("timeline job " + jobId + " did not finish");
    }

    private static AddAreaToFavoritesDto area(String name, double[] place) {
        double dLat = 90 / 111_320.0;
        double dLon = 90 / (111_320.0 * Math.cos(Math.toRadians(place[0])));
        return new AddAreaToFavoritesDto(name, place[0] + dLat, place[1] + dLon, place[0] - dLat, place[1] - dLon);
    }

    private static Instant at(String time) {
        return Instant.parse("2026-09-08T" + time + ":00Z");
    }

    /** Home, office and an evening at the gym, in a coordinate scope of its own. */
    private static final class Day {
        private final Track places = new Track();
        final double[] home = places.place(49.8420, 24.0310);
        final double[] office = places.place(49.8350, 24.0750);
        final double[] gym = places.place(49.8452, 24.0602);

        List<OwnTracksLocationMessage> all() {
            return new Track()
                    .stay(home, at("06:00"), at("08:00"), 5)
                    .drive(home, office, at("08:01"), at("08:20"))
                    .stay(office, at("08:21"), at("16:40"), 10)
                    .drive(office, gym, at("16:41"), at("16:59"))
                    .stay(gym, at("17:00"), at("19:59"), 10)
                    .messages();
        }
    }
}
