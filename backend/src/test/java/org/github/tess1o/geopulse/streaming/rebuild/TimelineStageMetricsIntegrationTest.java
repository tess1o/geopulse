package org.github.tess1o.geopulse.streaming.rebuild;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.model.TimelineJobProgress;
import org.github.tess1o.geopulse.streaming.model.TimelineJobStep;
import org.github.tess1o.geopulse.streaming.service.AsyncTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.TimelineJobProgressService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.WorkloadMetricsTestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a slow regeneration needs to report so its time can be attributed: detection and place resolution as separate
 * stages, and how the cached places were found.
 */
@QuarkusTest
@TestProfile(WorkloadMetricsTestProfile.class)
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineStageMetricsIntegrationTest {

    private static final String STAGE_TIMER = "geopulse.timeline.regeneration.stage.duration";

    @Inject
    TimelineReplayHarness harness;

    @Inject
    AsyncTimelineGenerationService asyncGeneration;

    @Inject
    TimelineJobProgressService jobs;

    @Inject
    MeterRegistry meters;

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void regenerationReportsDetectionAndPlaceResolutionSeparately() throws InterruptedException {
        UUID userId = harness.createUser("stage-metrics");
        Track track = new Track();
        double[] home = track.place(49.8420, 24.0310);
        double[] office = track.place(49.8350, 24.0750);
        harness.ingest(userId, track
                .stay(home, at("06:00"), at("08:00"), 5)
                .drive(home, office, at("08:01"), at("08:20"))
                .stay(office, at("08:21"), at("12:00"), 10)
                .messages());
        long detection = stageCount("state_machine");
        long resolution = stageCount("location_resolution");

        TimelineJobProgress job = awaitJob(asyncGeneration.regenerateTimelineAsync(userId));

        assertThat(stageCount("state_machine") - detection).isEqualTo(1);
        assertThat(stageCount("location_resolution") - resolution).isEqualTo(1);
        TimelineJobStep geocoding = job.getSteps().stream()
                .filter(step -> step.getPhase().equals("geocoding")).findFirst().orElseThrow();
        assertThat(geocoding.getCounters())
                .containsKeys("batchResolved", "individualResolved")
                .containsEntry("individualResolved", 0);
    }

    private long stageCount(String stage) {
        Timer timer = meters.find(STAGE_TIMER).tag("stage", stage).tag("trigger", "manual").timer();
        return timer == null ? 0 : timer.count();
    }

    private TimelineJobProgress awaitJob(UUID jobId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (Instant.now().isBefore(deadline)) {
            TimelineJobProgress progress = jobs.getJobProgress(jobId).orElseThrow();
            if (progress.isTerminal()) {
                assertThat(progress.getStatus()).isEqualTo(TimelineJobProgress.JobStatus.COMPLETED);
                return progress;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("timeline job " + jobId + " did not finish");
    }

    private static Instant at(String time) {
        return Instant.parse("2026-09-08T" + time + ":00Z");
    }
}
