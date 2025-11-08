package org.github.tess1o.geopulse.streaming.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents the progress of a timeline generation job.
 * Stored in-memory with a 1-hour retention period.
 */
@Data
@Builder
public class TimelineJobProgress {

    /**
     * Unique identifier for this job
     */
    private UUID jobId;

    /**
     * User ID who initiated this job
     */
    private UUID userId;

    /**
     * Current status of the job
     */
    private JobStatus status;

    /**
     * Human-readable description of the current step
     * e.g., "Loading GPS data", "Reverse geocoding locations"
     */
    private String currentStep;

    /**
     * Index of the current step (1-9)
     */
    private int currentStepIndex;

    /**
     * Total number of steps in the job (always 9)
     */
    private int totalSteps;

    /**
     * Overall progress percentage (0-100)
     */
    private int progressPercentage;

    /**
     * When the job was started
     */
    private Instant startTime;

    /**
     * When the job completed (success or failure). Null if still running.
     */
    private Instant endTime;

    /**
     * Error message if the job failed. Null if successful or still running.
     */
    private String errorMessage;

    /**
     * Additional details specific to the current step.
     * Examples:
     * - GPS loading: { "gpsPointsLoaded": 5000, "totalGpsPoints": 10000 }
     * - Geocoding: { "totalLocations": 100, "favoritesResolved": 70, "cachedResolved": 20, "externalPending": 10 }
     */
    @Builder.Default
    private Map<String, Object> details = new HashMap<>();

    /**
     * Job status enumeration
     */
    public enum JobStatus {
        /**
         * Job is queued and waiting to start
         */
        QUEUED,

        /**
         * Job is currently running
         */
        RUNNING,

        /**
         * Job completed successfully
         */
        COMPLETED,

        /**
         * Job failed with an error
         */
        FAILED
    }

    /**
     * Calculate the duration of the job in milliseconds.
     * Returns 0 if the job hasn't started yet.
     * Returns the duration so far if the job is still running.
     */
    public long getDurationMs() {
        if (startTime == null) {
            return 0;
        }

        Instant end = endTime != null ? endTime : Instant.now();
        return end.toEpochMilli() - startTime.toEpochMilli();
    }

    /**
     * Check if the job is in a terminal state (completed or failed)
     */
    public boolean isTerminal() {
        return status == JobStatus.COMPLETED || status == JobStatus.FAILED;
    }

    /**
     * Check if the job is still active (queued or running)
     */
    public boolean isActive() {
        return status == JobStatus.QUEUED || status == JobStatus.RUNNING;
    }
}
