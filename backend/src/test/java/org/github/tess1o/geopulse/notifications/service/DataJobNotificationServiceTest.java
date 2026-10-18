package org.github.tess1o.geopulse.notifications.service;

import org.github.tess1o.geopulse.export.model.ExportFormat;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.export.model.ExportStatus;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportStatus;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationSource;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class DataJobNotificationServiceTest {

    private final UUID userId = UUID.randomUUID();

    @Test
    void exportNotification_completedJobLinksToItsDownload() {
        ExportJob job = new ExportJob(userId, List.of("rawgps"), null, ExportFormat.GPX);
        job.setStatus(ExportStatus.COMPLETED);
        job.setFileSizeBytes(5 * 1024 * 1024);

        DataJobNotificationService.DataJobNotification notification =
                DataJobNotificationService.exportNotification(job, 24);

        assertThat(notification).isNotNull();
        assertThat(notification.userId()).isEqualTo(userId);
        assertThat(notification.source()).isEqualTo(NotificationSource.EXPORT);
        assertThat(notification.type()).isEqualTo(NotificationType.EXPORT_COMPLETED);
        assertThat(notification.dedupeKey()).isEqualTo("export:" + job.getJobId() + ":EXPORT_COMPLETED");
        assertThat(notification.metadata())
                .containsEntry("targetRoute", "/app/data-export-import?tab=export&exportJobId=" + job.getJobId())
                .containsEntry("format", "gpx")
                .containsEntry("fileSizeBytes", 5L * 1024 * 1024);
        assertThat(notification.message()).contains("GPX", "5.0 MB", "24 hours");
    }

    @Test
    void exportNotification_failedJobCarriesTheError() {
        ExportJob job = new ExportJob(userId, List.of("rawgps"), null, ExportFormat.GEOPULSE);
        job.setStatus(ExportStatus.FAILED);
        job.setError("disk full");

        DataJobNotificationService.DataJobNotification notification =
                DataJobNotificationService.exportNotification(job, 24);

        assertThat(notification).isNotNull();
        assertThat(notification.type()).isEqualTo(NotificationType.EXPORT_FAILED);
        assertThat(notification.dedupeKey()).isEqualTo("export:" + job.getJobId() + ":EXPORT_FAILED");
        assertThat(notification.message()).contains("GeoPulse", "disk full");
        assertThat(notification.metadata()).doesNotContainKey("fileSizeBytes");
    }

    @Test
    void exportNotification_skipsRunningJobs() {
        ExportJob job = new ExportJob(userId, List.of("rawgps"), null, ExportFormat.CSV);

        assertThat(DataJobNotificationService.exportNotification(job, 24)).isNull();
    }

    @Test
    void importNotification_completedAndFailed() {
        ImportJob job = new ImportJob(userId, null, "trip.gpx", new byte[0]);
        assertThat(DataJobNotificationService.importNotification(job)).isNull();

        job.setStatus(ImportStatus.COMPLETED);
        DataJobNotificationService.DataJobNotification completed = DataJobNotificationService.importNotification(job);
        assertThat(completed.source()).isEqualTo(NotificationSource.IMPORT);
        assertThat(completed.type()).isEqualTo(NotificationType.IMPORT_COMPLETED);
        assertThat(completed.dedupeKey()).isEqualTo("import:" + job.getJobId() + ":IMPORT_COMPLETED");
        assertThat(completed.message()).contains("trip.gpx");
        assertThat(completed.metadata()).containsEntry("targetRoute", "/app/data-export-import?tab=import");

        job.setStatus(ImportStatus.FAILED);
        job.setError("x".repeat(500));
        DataJobNotificationService.DataJobNotification failed = DataJobNotificationService.importNotification(job);
        assertThat(failed.type()).isEqualTo(NotificationType.IMPORT_FAILED);
        assertThat(failed.message()).hasSizeLessThan(400).endsWith("…");
    }

    @Test
    void formatSize_usesBinaryUnits() {
        assertThat(DataJobNotificationService.formatSize(512)).isEqualTo("512 B");
        assertThat(DataJobNotificationService.formatSize(1536)).isEqualTo("1.5 KB");
        assertThat(DataJobNotificationService.formatSize(3L * 1024 * 1024 * 1024)).isEqualTo("3.0 GB");
    }
}
