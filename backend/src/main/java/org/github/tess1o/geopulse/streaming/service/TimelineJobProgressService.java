package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.model.TimelineJobProgress;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for managing timeline generation job progress tracking.
 * Jobs are stored in-memory with a 1-hour retention period for completed jobs.
 */
@ApplicationScoped
@Slf4j
public class TimelineJobProgressService {

    private static final int TOTAL_STEPS = 9;
    private static final int MAX_JOBS_IN_MEMORY = 1000;
    private static final Duration JOB_RETENTION_PERIOD = Duration.ofHours(24);

    /**
     * In-memory storage of job progress indexed by job ID
     */
    private final ConcurrentHashMap<UUID, TimelineJobProgress> jobStore = new ConcurrentHashMap<>();

    /**
     * Index of active jobs by user ID for quick lookup
     */
    private final ConcurrentHashMap<UUID, UUID> userActiveJobIndex = new ConcurrentHashMap<>();

    /**
     * Create a new timeline generation job for a user
     *
     * @param userId The user ID
     * @return The job ID
     */
    public UUID createJob(UUID userId) {
        UUID jobId = UUID.randomUUID();

        TimelineJobProgress job = TimelineJobProgress.builder()
                .jobId(jobId)
                .userId(userId)
                .status(TimelineJobProgress.JobStatus.QUEUED)
                .currentStep("Initializing timeline generation")
                .currentStepIndex(0)
                .totalSteps(TOTAL_STEPS)
                .progressPercentage(0)
                .startTime(Instant.now())
                .details(new HashMap<>())
                .build();

        jobStore.put(jobId, job);
        userActiveJobIndex.put(userId, jobId);

        log.info("Created timeline generation job {} for user {}", jobId, userId);

        // Enforce memory limit
        enforceMemoryLimit();

        return jobId;
    }

    /**
     * Update the progress of a job
     *
     * @param jobId The job ID
     * @param step Human-readable step description
     * @param stepIndex Step index (1-9)
     * @param percentage Progress percentage (0-100)
     * @param details Additional step-specific details
     */
    public void updateProgress(UUID jobId, String step, int stepIndex, int percentage, Map<String, Object> details) {
        TimelineJobProgress job = jobStore.get(jobId);
        if (job == null) {
            log.warn("Attempted to update non-existent job {}", jobId);
            return;
        }

        job.setStatus(TimelineJobProgress.JobStatus.RUNNING);
        job.setCurrentStep(step);
        job.setCurrentStepIndex(stepIndex);
        job.setProgressPercentage(Math.min(100, Math.max(0, percentage)));

        if (details != null) {
            job.getDetails().putAll(details);
        }

        log.debug("Job {} progress: {}% - {}", jobId, percentage, step);
    }

    /**
     * Mark a job as completed successfully
     *
     * @param jobId The job ID
     */
    public void completeJob(UUID jobId) {
        TimelineJobProgress job = jobStore.get(jobId);
        if (job == null) {
            log.warn("Attempted to complete non-existent job {}", jobId);
            return;
        }

        job.setStatus(TimelineJobProgress.JobStatus.COMPLETED);
        job.setCurrentStep("Timeline generation completed");
        job.setProgressPercentage(100);
        job.setEndTime(Instant.now());

        // Remove from active job index
        userActiveJobIndex.remove(job.getUserId());

        log.info("Job {} completed successfully in {}ms", jobId, job.getDurationMs());
    }

    /**
     * Mark a job as failed with an error message
     *
     * @param jobId The job ID
     * @param errorMessage The error message
     */
    public void failJob(UUID jobId, String errorMessage) {
        TimelineJobProgress job = jobStore.get(jobId);
        if (job == null) {
            log.warn("Attempted to fail non-existent job {}", jobId);
            return;
        }

        job.setStatus(TimelineJobProgress.JobStatus.FAILED);
        job.setErrorMessage(errorMessage);
        job.setEndTime(Instant.now());

        // Remove from active job index
        userActiveJobIndex.remove(job.getUserId());

        log.error("Job {} failed after {}ms: {}", jobId, job.getDurationMs(), errorMessage);
    }

    /**
     * Get the progress of a specific job
     *
     * @param jobId The job ID
     * @return The job progress, or empty if not found
     */
    public Optional<TimelineJobProgress> getJobProgress(UUID jobId) {
        return Optional.ofNullable(jobStore.get(jobId));
    }

    /**
     * Get the active (queued or running) job for a user, if any
     *
     * @param userId The user ID
     * @return The active job progress, or empty if no active job exists
     */
    public Optional<TimelineJobProgress> getUserActiveJob(UUID userId) {
        UUID jobId = userActiveJobIndex.get(userId);
        if (jobId == null) {
            return Optional.empty();
        }

        TimelineJobProgress job = jobStore.get(jobId);
        if (job == null || !job.isActive()) {
            // Clean up stale index entry
            userActiveJobIndex.remove(userId);
            return Optional.empty();
        }

        return Optional.of(job);
    }

    /**
     * Get historical (completed or failed) jobs for a user, sorted by start time descending
     *
     * @param userId The user ID
     * @return List of historical jobs (completed or failed), sorted by most recent first
     */
    public List<TimelineJobProgress> getUserHistoryJobs(UUID userId) {
        return jobStore.values().stream()
                .filter(job -> job.getUserId().equals(userId))
                .filter(TimelineJobProgress::isTerminal) // Only completed or failed jobs
                .sorted(Comparator.comparing(TimelineJobProgress::getStartTime).reversed()) // Most recent first
                .collect(Collectors.toList());
    }

    /**
     * Clean up old completed jobs to prevent memory leaks.
     * Called by the cleanup scheduler.
     *
     * @return The number of jobs removed
     */
    public int cleanupOldJobs() {
        Instant cutoffTime = Instant.now().minus(JOB_RETENTION_PERIOD);

        List<UUID> jobsToRemove = jobStore.values().stream()
                .filter(TimelineJobProgress::isTerminal)
                .filter(job -> job.getEndTime() != null && job.getEndTime().isBefore(cutoffTime))
                .map(TimelineJobProgress::getJobId)
                .collect(Collectors.toList());

        for (UUID jobId : jobsToRemove) {
            jobStore.remove(jobId);
        }

        if (!jobsToRemove.isEmpty()) {
            log.info("Cleaned up {} old timeline generation jobs", jobsToRemove.size());
        }

        return jobsToRemove.size();
    }

    /**
     * Get statistics about jobs in memory
     *
     * @return Map with statistics
     */
    public Map<String, Object> getStatistics() {
        long total = jobStore.size();
        long queued = jobStore.values().stream().filter(j -> j.getStatus() == TimelineJobProgress.JobStatus.QUEUED).count();
        long running = jobStore.values().stream().filter(j -> j.getStatus() == TimelineJobProgress.JobStatus.RUNNING).count();
        long completed = jobStore.values().stream().filter(j -> j.getStatus() == TimelineJobProgress.JobStatus.COMPLETED).count();
        long failed = jobStore.values().stream().filter(j -> j.getStatus() == TimelineJobProgress.JobStatus.FAILED).count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalJobs", total);
        stats.put("queuedJobs", queued);
        stats.put("runningJobs", running);
        stats.put("completedJobs", completed);
        stats.put("failedJobs", failed);
        stats.put("activeUserJobs", userActiveJobIndex.size());

        return stats;
    }

    /**
     * Enforce memory limit by removing oldest completed jobs if we exceed the limit
     */
    private void enforceMemoryLimit() {
        if (jobStore.size() <= MAX_JOBS_IN_MEMORY) {
            return;
        }

        // Remove oldest completed jobs first
        List<UUID> completedJobIds = jobStore.values().stream()
                .filter(TimelineJobProgress::isTerminal)
                .sorted(Comparator.comparing(TimelineJobProgress::getEndTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(TimelineJobProgress::getJobId)
                .limit(jobStore.size() - MAX_JOBS_IN_MEMORY)
                .collect(Collectors.toList());

        for (UUID jobId : completedJobIds) {
            jobStore.remove(jobId);
        }

        log.warn("Enforced memory limit: removed {} old jobs. Current size: {}", completedJobIds.size(), jobStore.size());
    }
}
