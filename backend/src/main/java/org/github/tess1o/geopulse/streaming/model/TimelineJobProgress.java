package org.github.tess1o.geopulse.streaming.model;

import lombok.Builder;
import lombok.Data;
import org.github.tess1o.geopulse.shared.api.MessageDescriptor;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents the progress of a timeline generation job.
 * Stored in memory (see {@code TimelineJobProgressService} for the retention period); lost when the backend restarts.
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
     * Translatable description of the current step. The frontend resolves {@code key} against its
     * catalog and falls back to {@code fallback} (English) when the key is missing, e.g. for a
     * language that has not been translated yet.
     */
    private MessageDescriptor currentStep;

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

    /** What started the job. */
    @Builder.Default
    private TimelineJobType type = TimelineJobType.OTHER;

    /** The steps the job went through so far, in order, with their timings, counters and memory. */
    @Builder.Default
    private List<TimelineJobStep> steps = new CopyOnWriteArrayList<>();

    /** The steps a job of this type usually goes through ({@link TimelineJobStep#getPhase()} values), for the page. */
    @Builder.Default
    private List<String> plannedSteps = List.of();

    /** Limits and kernel memory peaks around the job. */
    private TimelineJobResources resources;

    /**
     * What the job worked on and produced: rebuilt period (dates), GPS points read, stays, trips and gaps written,
     * corrections applied and not applied.
     */
    @Builder.Default
    private Map<String, Object> summary = new ConcurrentHashMap<>();

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
