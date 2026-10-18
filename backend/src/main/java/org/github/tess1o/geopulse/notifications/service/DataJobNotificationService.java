package org.github.tess1o.geopulse.notifications.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.export.model.ExportFormat;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.export.model.ExportStatus;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportStatus;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationSource;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * In-app notifications for finished export and import jobs, so users who left the Data Export/Import
 * page (or closed the tab) learn that their file is ready or that the job failed.
 *
 * Both job types run on scheduler threads, so publishing happens in its own transaction and never
 * throws: a notification problem must not fail or retry the job itself.
 */
@ApplicationScoped
@Slf4j
public class DataJobNotificationService {

    static final String EXPORT_ROUTE = "/app/data-export-import?tab=export";
    static final String IMPORT_ROUTE = "/app/data-export-import?tab=import";
    private static final int MAX_ERROR_LENGTH = 300;

    private final UserRepository userRepository;
    private final NotificationPublisherService publisher;
    private final SystemSettingsService settingsService;

    public DataJobNotificationService(UserRepository userRepository, NotificationPublisherService publisher,
                                      SystemSettingsService settingsService) {
        this.userRepository = userRepository;
        this.publisher = publisher;
        this.settingsService = settingsService;
    }

    record DataJobNotification(UUID userId, NotificationSource source, NotificationType type, String title,
                               String message, Map<String, Object> metadata, String dedupeKey) {
    }

    public void exportFinished(ExportJob job) {
        DataJobNotification notification = exportNotification(job, settingsService.getInteger("export.job-expiry-hours"));
        if (notification != null) {
            publishSafely(notification);
        }
    }

    public void importFinished(ImportJob job) {
        DataJobNotification notification = importNotification(job);
        if (notification != null) {
            publishSafely(notification);
        }
    }

    static DataJobNotification exportNotification(ExportJob job, int expiryHours) {
        boolean completed = job.getStatus() == ExportStatus.COMPLETED;
        if (!completed && job.getStatus() != ExportStatus.FAILED) {
            return null;
        }
        NotificationType type = completed ? NotificationType.EXPORT_COMPLETED : NotificationType.EXPORT_FAILED;
        String format = formatLabel(job.getFormat());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("exportJobId", job.getJobId().toString());
        metadata.put("format", job.getFormat() == null ? null : job.getFormat().value());
        metadata.put("targetRoute", EXPORT_ROUTE + "&exportJobId=" + job.getJobId());

        String title;
        String message;
        if (completed) {
            metadata.put("fileSizeBytes", job.getFileSizeBytes());
            title = "Your export is ready";
            message = "Your " + format + " export (" + formatSize(job.getFileSizeBytes()) + ") is ready to download. "
                    + "It stays available for " + expiryHours + " hours.";
        } else {
            title = "Export failed";
            message = "Your " + format + " export could not be completed: " + errorText(job.getError());
        }
        return new DataJobNotification(job.getUserId(), NotificationSource.EXPORT, type, title, message, metadata,
                "export:" + job.getJobId() + ":" + type);
    }

    static DataJobNotification importNotification(ImportJob job) {
        boolean completed = job.getStatus() == ImportStatus.COMPLETED;
        if (!completed && job.getStatus() != ImportStatus.FAILED) {
            return null;
        }
        NotificationType type = completed ? NotificationType.IMPORT_COMPLETED : NotificationType.IMPORT_FAILED;
        String fileName = job.getUploadedFileName() == null ? "Your file" : job.getUploadedFileName();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("importJobId", job.getJobId().toString());
        metadata.put("fileName", job.getUploadedFileName());
        metadata.put("targetRoute", IMPORT_ROUTE);

        String title = completed ? "Import completed" : "Import failed";
        String message = completed
                ? fileName + " was imported successfully."
                : fileName + " could not be imported: " + errorText(job.getError());
        return new DataJobNotification(job.getUserId(), NotificationSource.IMPORT, type, title, message, metadata,
                "import:" + job.getJobId() + ":" + type);
    }

    private void publishSafely(DataJobNotification notification) {
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                UserEntity user = userRepository.findById(notification.userId());
                if (user == null) {
                    log.debug("Skipping {} notification: user {} no longer exists", notification.type(), notification.userId());
                    return;
                }
                publisher.publish(user, notification.source(), notification.type(), notification.title(),
                        notification.message(), notification.metadata(), notification.dedupeKey(), null);
            });
        } catch (Exception e) {
            log.warn("Failed to publish {} notification for user {}: {}",
                    notification.type(), notification.userId(), e.getMessage(), e);
        }
    }

    private static String formatLabel(ExportFormat format) {
        if (format == null) {
            return "data";
        }
        return switch (format) {
            case GEOPULSE -> "GeoPulse";
            case OWNTRACKS -> "OwnTracks";
            case GEOJSON -> "GeoJSON";
            case GPX -> "GPX";
            case CSV -> "CSV";
        };
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    private static String errorText(String error) {
        if (error == null || error.isBlank()) {
            return "unknown error";
        }
        return error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) + "…" : error;
    }
}
