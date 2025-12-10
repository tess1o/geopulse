package org.github.tess1o.geopulse.geocoding.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Model representing the progress of a bulk reconciliation job.
 * Similar to TimelineJobProgress but simplified for reconciliation use case.
 */
@Data
@Builder
public class ReconciliationJobProgress {
    private UUID jobId;
    private UUID userId;
    private JobStatus status;
    private String providerName;

    // Progress tracking
    private int progressPercentage;  // 0-100
    private int totalItems;
    private int processedItems;
    private int successCount;
    private int failedCount;

    // Timestamps
    private Instant startTime;
    private Instant endTime;

    // Error handling
    private String errorMessage;

    public enum JobStatus {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED
    }

    /**
     * Check if the job is in a terminal state (completed or failed).
     */
    public boolean isTerminal() {
        return status == JobStatus.COMPLETED || status == JobStatus.FAILED;
    }

    /**
     * Check if the job is still active (queued or running).
     */
    public boolean isActive() {
        return status == JobStatus.QUEUED || status == JobStatus.RUNNING;
    }

    /**
     * Get the duration of the job in milliseconds.
     */
    public long getDurationMs() {
        if (startTime == null) return 0;
        Instant end = endTime != null ? endTime : Instant.now();
        return end.toEpochMilli() - startTime.toEpochMilli();
    }
}
