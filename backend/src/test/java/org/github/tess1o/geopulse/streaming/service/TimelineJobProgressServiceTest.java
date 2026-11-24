package org.github.tess1o.geopulse.streaming.service;

import org.github.tess1o.geopulse.streaming.model.TimelineJobMemory;
import org.github.tess1o.geopulse.streaming.model.TimelineJobProgress;
import org.github.tess1o.geopulse.streaming.model.TimelineJobStep;
import org.github.tess1o.geopulse.streaming.model.TimelineJobType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class TimelineJobProgressServiceTest {

    @Test
    void updateProgressDoesNotRegressWhenLaterStepReportsLowerPercentage() {
        TimelineJobProgressService service = new TimelineJobProgressService();
        UUID userId = UUID.randomUUID();
        UUID jobId = service.createJob(userId);

        service.updateProgress(jobId, TimelineJobProgressService.step("processingStateMachine", "Processing GPS points through state machine", null), 4, 55, null);
        service.updateProgress(jobId, TimelineJobProgressService.step("geocodingLocationProgress", "Geocoding location 1/10", null), 4, 40, null);

        TimelineJobProgress progress = service.getJobProgress(jobId).orElseThrow();

        assertThat(progress.getProgressPercentage()).isEqualTo(55);
        assertThat(progress.getCurrentStep().fallback()).isEqualTo("Geocoding location 1/10");
    }

    private static void progress(TimelineJobProgressService service, UUID jobId, String key, Map<String, Object> details) {
        service.updateProgress(jobId, TimelineJobProgressService.step(key, "Message " + key, details), 4, 50, details);
    }

    @Test
    void messagesOfOneStepAreOneStepWithItsCountersAndMemory() {
        TimelineJobProgressService service = new TimelineJobProgressService();
        UUID jobId = service.createJob(UUID.randomUUID(), TimelineJobType.FULL_REBUILD);

        progress(service, jobId, "cleaningUp", null);
        progress(service, jobId, "preparingGpsProcessing", null);
        progress(service, jobId, "processingStateMachine", Map.of("processedPoints", 5000));
        progress(service, jobId, "processingStateMachine", Map.of("processedPoints", 10000));
        progress(service, jobId, "startingLocationResolution", Map.of("totalLocations", 30));
        for (int i = 1; i <= 3; i++) {
            progress(service, jobId, "geocodingLocationProgress", Map.of("externalCompleted", i));
        }
        progress(service, jobId, "postProcessingTrips", null);
        progress(service, jobId, "persistingTimeline", null);
        service.completeJob(jobId);

        TimelineJobProgress job = service.getJobProgress(jobId).orElseThrow();
        assertThat(job.getSteps()).extracting(TimelineJobStep::getPhase)
                .containsExactly("preparing", "processingPoints", "geocoding", "postProcessingTrips", "persisting");
        assertThat(job.getSteps()).allSatisfy(step -> {
            assertThat(step.getEndedAt()).isNotNull();
            assertThat(step.getDurationMs()).isGreaterThanOrEqualTo(0);
            assertThat(step.isFailed()).isFalse();
            assertThat(step.getMemoryEnd()).isNotNull();
            assertThat(step.getMemoryMax().heapUsedBytes()).isPositive();
            assertThat(step.getTitle().key()).isEqualTo("timelineJobs.steps." + step.getPhase());
        });
        assertThat(job.getSteps().get(1).getCounters()).containsEntry("processedPoints", 10000);
        assertThat(job.getSteps().get(2).getCounters())
                .containsEntry("totalLocations", 30).containsEntry("externalCompleted", 3);
        // Ends of consecutive steps meet
        for (int i = 1; i < job.getSteps().size(); i++) {
            assertThat(job.getSteps().get(i).getStartedAt()).isEqualTo(job.getSteps().get(i - 1).getEndedAt());
        }
        assertThat(job.getType()).isEqualTo(TimelineJobType.FULL_REBUILD);
        assertThat(job.getPlannedSteps()).startsWith("preparing", "processingPoints", "geocoding")
                .doesNotContain("badges");
        assertThat(job.getResources().cpuCount()).isPositive();
        assertThat(job.getResources().heapMaxBytes()).isPositive();
    }

    @Test
    void theCompletedMessageEndsTheLastStepWithoutAddingOne() {
        TimelineJobProgressService service = new TimelineJobProgressService();
        UUID jobId = service.createJob(UUID.randomUUID(), TimelineJobType.FULL_REBUILD);
        progress(service, jobId, "finalizing", null);

        service.updateProgress(jobId, TimelineJobProgressService.step("completed", "Timeline generation completed", null),
                9, 100, null);
        TimelineJobProgress job = service.getJobProgress(jobId).orElseThrow();
        assertThat(job.getSteps()).extracting(TimelineJobStep::getPhase).containsExactly("finalizing");
        assertThat(job.getSteps().getLast().getEndedAt()).isNotNull();

        service.completeJob(jobId);
        assertThat(job.getSteps()).hasSize(1);
        assertThat(job.getCurrentStep().fallback()).isEqualTo("Timeline generation completed");
    }

    @Test
    void aStepReachedAgainLaterIsANewStep() {
        TimelineJobProgressService service = new TimelineJobProgressService();
        UUID jobId = service.createJob(UUID.randomUUID(), TimelineJobType.FULL_REBUILD);

        progress(service, jobId, "startingLocationResolution", null);
        progress(service, jobId, "mergingTimeline", null);
        progress(service, jobId, "applyingCorrections", null);
        progress(service, jobId, "startingLocationResolution", null);
        progress(service, jobId, "persistingTimeline", null);

        assertThat(service.getJobProgress(jobId).orElseThrow().getSteps()).extracting(TimelineJobStep::getPhase)
                .containsExactly("geocoding", "merging", "corrections", "geocoding", "persisting");
    }

    @Test
    void aFailedJobEndsItsStepAsFailedAndRecordsItsPeaks() {
        TimelineJobProgressService service = new TimelineJobProgressService();
        UUID jobId = service.createJob(UUID.randomUUID(), TimelineJobType.IMPORT);
        progress(service, jobId, "processingStateMachine", null);

        service.failJob(jobId, "boom");

        TimelineJobProgress job = service.getJobProgress(jobId).orElseThrow();
        TimelineJobStep last = job.getSteps().getLast();
        assertThat(last.getPhase()).isEqualTo("processingPoints");
        assertThat(last.isFailed()).isTrue();
        assertThat(last.getEndedAt()).isNotNull();
        assertThat(job.getStatus()).isEqualTo(TimelineJobProgress.JobStatus.FAILED);
    }

    @Test
    void anUnknownMessageIsItsOwnStepTitledByItsMessage() {
        TimelineJobProgressService service = new TimelineJobProgressService();
        UUID jobId = service.createJob(UUID.randomUUID());
        service.updateProgress(jobId, TimelineJobProgressService.step("somethingNew", "Doing something new", null),
                5, 60, null);

        TimelineJobStep step = service.getJobProgress(jobId).orElseThrow().getSteps().getFirst();
        assertThat(step.getPhase()).isEqualTo("somethingNew");
        assertThat(step.getTitle().fallback()).isEqualTo("Doing something new");
        assertThat(service.getJobProgress(jobId).orElseThrow().getType()).isEqualTo(TimelineJobType.OTHER);
    }

    @Test
    void plannedStepsFollowTheJobType() {
        assertThat(TimelineJobProgressService.plannedSteps(TimelineJobType.CORRECTION)).containsExactly("correctionWindow");
        // Imports and campaigns recalculate badges; a rebuild started from the app does not
        assertThat(TimelineJobProgressService.plannedSteps(TimelineJobType.CAMPAIGN)).endsWith("finalizing", "badges");
        assertThat(TimelineJobProgressService.plannedSteps(TimelineJobType.IMPORT)).endsWith("finalizing", "badges");
        assertThat(TimelineJobProgressService.plannedSteps(TimelineJobType.FULL_REBUILD)).endsWith("persisting", "finalizing");
        assertThat(TimelineJobProgressService.plannedSteps(TimelineJobType.OTHER)).isEmpty();
    }

    @Test
    void theSamplerKeepsTheHighestMemoryOfTheRunningStep() {
        TimelineJobProgressService service = new TimelineJobProgressService();
        UUID jobId = service.createJob(UUID.randomUUID(), TimelineJobType.FULL_REBUILD);
        progress(service, jobId, "processingStateMachine", null);
        TimelineJobStep step = service.getJobProgress(jobId).orElseThrow().getSteps().getLast();
        step.sample(new TimelineJobMemory(Long.MAX_VALUE / 2, 1, 1));

        service.sampleRunningJobs();

        assertThat(step.getMemoryMax().heapUsedBytes()).isEqualTo(Long.MAX_VALUE / 2);
        assertThat(step.getMemoryMax().processBytes()).isGreaterThanOrEqualTo(1);
        service.completeJob(jobId);
        service.shutdown();
    }

    @Test
    void aStepEndsOnce() {
        TimelineJobStep step = new TimelineJobStep("geocoding", null, Instant.parse("2026-01-01T00:00:00Z"),
                new TimelineJobMemory(10, 20, 30));
        step.end(Instant.parse("2026-01-01T00:00:05Z"), new TimelineJobMemory(5, 40, 25), false);
        step.end(Instant.parse("2026-01-01T00:01:00Z"), new TimelineJobMemory(1, 1, 1), true);

        assertThat(step.getDurationMs()).isEqualTo(5000);
        assertThat(step.isFailed()).isFalse();
        assertThat(step.getMemoryEnd()).isEqualTo(new TimelineJobMemory(5, 40, 25));
        assertThat(step.getMemoryMax()).isEqualTo(new TimelineJobMemory(10, 40, 30));
    }

    @Test
    void summaryKeepsTheValuesOfItsJobOnly() {
        TimelineJobProgressService service = new TimelineJobProgressService();
        UUID jobId = service.createJob(UUID.randomUUID(), TimelineJobType.FULL_REBUILD);
        service.recordSummary(jobId, Map.of("gpsPoints", 42L, "rebuiltFrom", "all"));
        service.recordSummary(null, Map.of("gpsPoints", 1L));
        service.recordSummary(UUID.randomUUID(), Map.of("gpsPoints", 2L));

        assertThat(service.getJobProgress(jobId).orElseThrow().getSummary())
                .containsExactlyInAnyOrderEntriesOf(Map.of("gpsPoints", 42L, "rebuiltFrom", "all"));
    }

    @Test
    void diagnosticsKeepOnlyTheMemoryFlagsOfJvmOptions() {
        assertThat(TimelineJobDiagnosticsService.memoryFlags(
                "-XX:MaximumHeapSizePercent=70 -Dquarkus.datasource.password=secret -Xmx512m  -XX:+ExitOnOutOfMemoryError"))
                .isEqualTo("-XX:MaximumHeapSizePercent=70 -Xmx512m -XX:+ExitOnOutOfMemoryError");
        assertThat(TimelineJobDiagnosticsService.memoryFlags("-Dsecret=1")).isEmpty();
        assertThat(List.of()).isEmpty();
    }
}
