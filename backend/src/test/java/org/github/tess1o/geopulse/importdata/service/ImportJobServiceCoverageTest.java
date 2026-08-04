package org.github.tess1o.geopulse.importdata.service;

import org.github.tess1o.geopulse.coverage.model.CoverageStatus;
import org.github.tess1o.geopulse.coverage.service.CoverageProcessingService;
import org.github.tess1o.geopulse.coverage.service.CoverageService;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.model.ImportStatus;
import org.github.tess1o.geopulse.insight.service.BadgeRecalculationService;
import org.github.tess1o.geopulse.streaming.model.TimelineJobProgress;
import org.github.tess1o.geopulse.streaming.service.TimelineJobProgressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class ImportJobServiceCoverageTest {

    @Mock
    ImportDataService importDataService;

    @Mock
    BadgeRecalculationService badgeRecalculationService;

    @Mock
    TimelineJobProgressService timelineJobProgressService;

    @Mock
    CoverageService coverageService;

    @Mock
    CoverageProcessingService coverageProcessingService;

    @InjectMocks
    ImportJobService importJobService;

    UUID userId;
    UUID timelineJobId;

    @BeforeEach
    void setUp() {
        importJobService.schedulerEnabled = true;
        userId = UUID.randomUUID();
        timelineJobId = UUID.randomUUID();
    }

    @Test
    void processImportJobs_waitsForImportOwnedCoverageThenCompletes() {
        ImportJob job = importJobService.createImportJob(
                userId,
                importOptions(),
                "locations.json",
                "{}".getBytes()
        );
        job.setStatus(ImportStatus.PROCESSING);
        job.setDetectedDataTypes(List.of("rawgps"));
        job.setDataProcessingCompleted(true);
        job.setGpsDataImported(true);
        job.setTimelineJobId(timelineJobId);

        TimelineJobProgress timelineJob = TimelineJobProgress.builder()
                .jobId(timelineJobId)
                .userId(userId)
                .status(TimelineJobProgress.JobStatus.COMPLETED)
                .currentStep("Timeline generation completed")
                .progressPercentage(100)
                .build();
        when(timelineJobProgressService.getJobProgress(timelineJobId))
                .thenReturn(Optional.of(timelineJob));
        when(coverageService.getCoverageStatus(userId))
                .thenReturn(
                        new CoverageStatus(true, false, true, null, null),
                        new CoverageStatus(true, false, true, null, null),
                        new CoverageStatus(true, true, true, null, null),
                        new CoverageStatus(true, false, true, null, null)
                );
        when(coverageProcessingService.startFullRecalculationAsync(userId)).thenReturn(true);

        importJobService.processImportJobs();

        assertThat(job.getStatus()).isEqualTo(ImportStatus.PROCESSING);
        assertThat(job.getProgress()).isEqualTo(95);
        assertThat(job.getProgressMessage()).isEqualTo("Recalculating coverage...");
        assertThat(job.isCoverageRecalculationStarted()).isTrue();

        importJobService.processImportJobs();

        assertThat(job.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(job.getProgress()).isEqualTo(100);
        assertThat(job.getProgressMessage()).isEqualTo("Import completed successfully");
        verify(coverageProcessingService, times(1)).startFullRecalculationAsync(userId);
        verifyNoInteractions(badgeRecalculationService);
    }

    @Test
    void processImportJobs_doesNotRegressImportProgressFromTimelineUpdate() {
        ImportJob job = importJobService.createImportJob(
                userId,
                importOptions(),
                "locations.json",
                "{}".getBytes()
        );
        job.setStatus(ImportStatus.PROCESSING);
        job.setDetectedDataTypes(List.of("rawgps"));
        job.setDataProcessingCompleted(true);
        job.setProgress(90);
        job.setTimelineJobId(timelineJobId);

        TimelineJobProgress timelineJob = TimelineJobProgress.builder()
                .jobId(timelineJobId)
                .userId(userId)
                .status(TimelineJobProgress.JobStatus.RUNNING)
                .currentStep("Geocoding location 1/10")
                .progressPercentage(20)
                .build();
        when(timelineJobProgressService.getJobProgress(timelineJobId))
                .thenReturn(Optional.of(timelineJob));

        importJobService.processImportJobs();

        assertThat(job.getProgress()).isEqualTo(90);
        assertThat(job.getProgressMessage()).isEqualTo("Timeline generation: Geocoding location 1/10");
    }

    private ImportOptions importOptions() {
        ImportOptions options = new ImportOptions();
        options.setImportFormat("owntracks");
        options.setDataTypes(List.of("rawgps"));
        return options;
    }
}
