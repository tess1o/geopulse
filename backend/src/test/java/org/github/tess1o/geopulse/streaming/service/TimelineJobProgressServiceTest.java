package org.github.tess1o.geopulse.streaming.service;

import org.github.tess1o.geopulse.streaming.model.TimelineJobProgress;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
}
