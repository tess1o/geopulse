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

        service.updateProgress(jobId, "Processing GPS points through state machine", 4, 55, null);
        service.updateProgress(jobId, "Geocoding location 1/10", 4, 40, null);

        TimelineJobProgress progress = service.getJobProgress(jobId).orElseThrow();

        assertThat(progress.getProgressPercentage()).isEqualTo(55);
        assertThat(progress.getCurrentStep()).isEqualTo("Geocoding location 1/10");
    }
}
