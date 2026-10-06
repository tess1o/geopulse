package org.github.tess1o.geopulse.export.model;

import lombok.Getter;
import lombok.Setter;
import org.github.tess1o.geopulse.shared.api.MessageDescriptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class ExportJob {

    /**
     * Frontend catalog namespace for {@link #step(String, String, Map)}. Keys live under
     * {@code exports.js}'s {@code progressMessages} block.
     */
    private static final String STEP_KEY_PREFIX = "exports.progressMessages.";

    /**
     * Build a translatable progress-message descriptor. {@code key} is a short suffix (e.g.
     * {@code "initializingCsv"}) appended to {@link #STEP_KEY_PREFIX}; {@code fallback} is the English
     * text shown when the active locale has no translation for the key yet; {@code params} are the
     * interpolation values (may be {@code null} for a message with no placeholders).
     */
    public static MessageDescriptor step(String key, String fallback, Map<String, Object> params) {
        return new MessageDescriptor(STEP_KEY_PREFIX + key, params, fallback);
    }

    private UUID jobId;
    private UUID userId;
    private ExportStatus status;
    private int progress;
    private MessageDescriptor progressMessage;
    private List<String> dataTypes;
    private ExportDateRange dateRange;
    private ExportFormat format;
    private GpxLayout gpxLayout = GpxLayout.SINGLE;
    private OwnTracksLayout owntracksLayout = OwnTracksLayout.OCAT;
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

    public ExportJob(UUID userId, List<String> dataTypes, ExportDateRange dateRange, ExportFormat format) {
        this();
        this.userId = userId;
        this.dataTypes = dataTypes;
        this.dateRange = dateRange;
        this.format = format;
    }

    /**
     * Updates the progress of the export job.
     * Thread-safe for concurrent updates.
     *
     * @param progress the progress percentage (0-100)
     * @param message the translatable progress-message descriptor describing current operation (see
     *                {@link #step})
     */
    public synchronized void updateProgress(int progress, MessageDescriptor message) {
        this.progress = Math.min(100, Math.max(0, progress));
        this.progressMessage = message;
    }

    /**
     * Convenience overload for a message with no interpolation params.
     *
     * @param stepKey short suffix appended to {@link #STEP_KEY_PREFIX} (see {@link #step})
     * @param fallback English text shown when the active locale has no translation for {@code stepKey}
     *                 yet
     */
    public void updateProgress(int progress, String stepKey, String fallback) {
        updateProgress(progress, step(stepKey, fallback, null));
    }

    /**
     * Convenience overload for a message with interpolation params.
     *
     * @param stepKey short suffix appended to {@link #STEP_KEY_PREFIX} (see {@link #step})
     * @param fallback English text shown when the active locale has no translation for {@code stepKey}
     *                 yet; also used as interpolation source together with {@code params}
     */
    public void updateProgress(int progress, String stepKey, String fallback, Map<String, Object> params) {
        updateProgress(progress, step(stepKey, fallback, params));
    }
}