package org.github.tess1o.geopulse.geocoding.service;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geocoding.model.ReconciliationJobProgress;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing in-memory progress tracking of bulk reconciliation jobs.
 * Based on the TimelineJobProgressService pattern.
 */
@ApplicationScoped
@Slf4j
public class ReconciliationJobProgressService {

    private static final int MAX_JOBS_IN_MEMORY = 1000;
    private static final Duration JOB_RETENTION_PERIOD = Duration.ofHours(24);

    // Thread-safe storage for job progress
    private final ConcurrentHashMap<UUID, ReconciliationJobProgress> jobStore = new ConcurrentHashMap<>();

    // Index for quickly finding active jobs by user
    private final ConcurrentHashMap<UUID, UUID> userActiveJobIndex = new ConcurrentHashMap<>();

    /**
     * Create a new reconciliation job.
     *
     * @param userId       User ID
     * @param providerName Provider name for reconciliation
     * @param totalItems   Total number of items to reconcile
     * @return Job ID
     */
    public UUID createJob(UUID userId, String providerName, int totalItems) {
        UUID jobId = UUID.randomUUID();

        ReconciliationJobProgress job = ReconciliationJobProgress.builder()
                .jobId(jobId)
                .userId(userId)
                .status(ReconciliationJobProgress.JobStatus.QUEUED)
                .providerName(providerName)
                .progressPercentage(0)
                .totalItems(totalItems)
                .processedItems(0)
                .successCount(0)
                .failedCount(0)
                .startTime(Instant.now())
                .build();

        jobStore.put(jobId, job);
        userActiveJobIndex.put(userId, jobId);

        log.info("Created reconciliation job {} for user {} ({} items, provider: {})",
                jobId, userId, totalItems, providerName);

        enforceMemoryLimit();
        return jobId;
    }

    /**
     * Update job progress.
     *
     * @param jobId          Job ID
     * @param processedItems Number of items processed so far
     * @param successCount   Number of successful reconciliations
     * @param failedCount    Number of failed reconciliations
     */
    public void updateProgress(UUID jobId, int processedItems, int successCount, int failedCount) {
        ReconciliationJobProgress job = jobStore.get(jobId);
        if (job == null) {
            log.warn("Attempted to update non-existent job {}", jobId);
            return;
        }

        job.setStatus(ReconciliationJobProgress.JobStatus.RUNNING);
        job.setProcessedItems(processedItems);
        job.setSuccessCount(successCount);
        job.setFailedCount(failedCount);

        // Calculate percentage
        int percentage = job.getTotalItems() > 0
                ? (int) ((processedItems * 100.0) / job.getTotalItems())
                : 0;
        job.setProgressPercentage(Math.min(100, percentage));

        log.debug("Job {} progress: {}/{} items ({} success, {} failed, {}%)",
                jobId, processedItems, job.getTotalItems(), successCount, failedCount, percentage);
    }

    /**
     * Mark job as completed.
     *
     * @param jobId Job ID
     */
    public void completeJob(UUID jobId) {
        ReconciliationJobProgress job = jobStore.get(jobId);
        if (job == null) {
            log.warn("Attempted to complete non-existent job {}", jobId);
            return;
        }

        job.setStatus(ReconciliationJobProgress.JobStatus.COMPLETED);
        job.setProgressPercentage(100);
        job.setEndTime(Instant.now());
        userActiveJobIndex.remove(job.getUserId());

        log.info("Job {} completed: {} items ({} success, {} failed) in {}ms",
                jobId, job.getTotalItems(), job.getSuccessCount(),
                job.getFailedCount(), job.getDurationMs());
    }

    /**
     * Mark job as failed.
     *
     * @param jobId        Job ID
     * @param errorMessage Error message
     */
    public void failJob(UUID jobId, String errorMessage) {
        ReconciliationJobProgress job = jobStore.get(jobId);
        if (job == null) {
            log.warn("Attempted to fail non-existent job {}", jobId);
            return;
        }

        job.setStatus(ReconciliationJobProgress.JobStatus.FAILED);
        job.setErrorMessage(errorMessage);
        job.setEndTime(Instant.now());
        userActiveJobIndex.remove(job.getUserId());

        log.error("Job {} failed after {}ms: {}", jobId, job.getDurationMs(), errorMessage);
    }

    /**
     * Get job progress by ID.
     *
     * @param jobId Job ID
     * @return Optional containing job progress, or empty if not found
     */
    public Optional<ReconciliationJobProgress> getJobProgress(UUID jobId) {
        return Optional.ofNullable(jobStore.get(jobId));
    }

    /**
     * Get user's active reconciliation job, if any.
     *
     * @param userId User ID
     * @return Optional containing active job, or empty if no active job
     */
    public Optional<ReconciliationJobProgress> getUserActiveJob(UUID userId) {
        UUID jobId = userActiveJobIndex.get(userId);
        if (jobId == null) {
            return Optional.empty();
        }

        ReconciliationJobProgress job = jobStore.get(jobId);
        if (job == null || !job.isActive()) {
            userActiveJobIndex.remove(userId);
            return Optional.empty();
        }

        return Optional.of(job);
    }

    /**
     * Clean up old completed jobs.
     * Removes jobs that completed more than 24 hours ago.
     *
     * @return Number of jobs removed
     */
    public int cleanupOldJobs() {
        Instant cutoffTime = Instant.now().minus(JOB_RETENTION_PERIOD);

        List<UUID> jobsToRemove = jobStore.values().stream()
                .filter(ReconciliationJobProgress::isTerminal)
                .filter(job -> job.getEndTime() != null && job.getEndTime().isBefore(cutoffTime))
                .map(ReconciliationJobProgress::getJobId)
                .toList();

        jobsToRemove.forEach(jobStore::remove);

        if (!jobsToRemove.isEmpty()) {
            log.info("Cleaned up {} old reconciliation jobs", jobsToRemove.size());
        }

        return jobsToRemove.size();
    }

    /**
     * Enforce memory limit by removing oldest completed jobs if limit exceeded.
     */
    private void enforceMemoryLimit() {
        if (jobStore.size() <= MAX_JOBS_IN_MEMORY) {
            return;
        }

        List<UUID> completedJobIds = jobStore.values().stream()
                .filter(ReconciliationJobProgress::isTerminal)
                .sorted(Comparator.comparing(ReconciliationJobProgress::getEndTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(ReconciliationJobProgress::getJobId)
                .limit(jobStore.size() - MAX_JOBS_IN_MEMORY)
                .toList();

        completedJobIds.forEach(jobStore::remove);

        if (!completedJobIds.isEmpty()) {
            log.warn("Enforced memory limit: removed {} old jobs", completedJobIds.size());
        }
    }
}
