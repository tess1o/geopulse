package org.github.tess1o.geopulse.export.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class ExportJob {
    private UUID jobId;
    private UUID userId;
    private ExportStatus status;
    private int progress;
    private String progressMessage;
    private List<String> dataTypes;
    private ExportDateRange dateRange;
    private String format;
    private Map<String, Object> options;
    private Instant createdAt;
    private Instant completedAt;
    private String tempFilePath;   // Path to temp file on disk (replaces in-memory byte arrays)
    private String contentType;    // MIME type for download
    private String fileExtension;  // File extension (.zip, .json, .gpx, .csv)
    private String error;
    private long fileSizeBytes;

    public ExportJob() {
        this.jobId = UUID.randomUUID();
        this.status = ExportStatus.PROCESSING;
        this.progress = 0;
        this.createdAt = Instant.now();
    }

    public ExportJob(UUID userId, List<String> dataTypes, ExportDateRange dateRange, String format) {
        this();
        this.userId = userId;
        this.dataTypes = dataTypes;
        this.dateRange = dateRange;
        this.format = format;
    }

    public ExportJob(UUID userId, List<String> dataTypes, ExportDateRange dateRange, String format, Map<String, Object> options) {
        this(userId, dataTypes, dateRange, format);
        this.options = options;
    }

    /**
     * Updates the progress of the export job.
     * Thread-safe for concurrent updates.
     *
     * @param progress the progress percentage (0-100)
     * @param message the progress message describing current operation
     */
    public synchronized void updateProgress(int progress, String message) {
        this.progress = Math.min(100, Math.max(0, progress));
        this.progressMessage = message;
    }
}