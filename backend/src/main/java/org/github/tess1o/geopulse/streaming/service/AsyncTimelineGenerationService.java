package org.github.tess1o.geopulse.streaming.service;

import io.smallrye.common.annotation.Identifier;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Service for executing timeline generation asynchronously.
 * This allows the REST endpoint to return immediately with a job ID
 * while the timeline generation runs in the background.
 */
@ApplicationScoped
@Slf4j
public class AsyncTimelineGenerationService {

    @Inject
    @Identifier("timeline-processing")
    ExecutorService executorService;

    @Inject
    StreamingTimelineGenerationService timelineGenerationService;

    @Inject
    TimelineJobProgressService jobProgressService;

    private final ConcurrentHashMap<UUID, Instant> pendingRegenerationStartTimes = new ConcurrentHashMap<>();

    public record TimelineSchedulingResult(
            UUID jobId,
            boolean scheduled,
            boolean queued
    ) {
    }

    /**
     * Start timeline regeneration asynchronously.
     * Creates a job, returns the job ID immediately, and runs the generation in the background.
     *
     * @param userId The user ID
     * @return The job ID for tracking progress
     * @throws IllegalStateException if a job is already running for this user
     */
    public UUID regenerateTimelineAsync(UUID userId) {
        return createAndRunAsyncJob(userId, jobId -> {
            log.info("Starting async full timeline regeneration for job {}", jobId);
            executeRegenerationWithTransaction(userId, jobId);
            finalizeJobAfterTimelineGeneration(jobId);
            log.info("Async full timeline regeneration completed for job {}", jobId);
        });
    }

    /**
     * Start timeline regeneration asynchronously from a specific timestamp.
     *
     * @param userId                    The user ID
     * @param earliestAffectedTimestamp The earliest timestamp that should be regenerated
     * @return The job ID for tracking progress
     * @throws IllegalStateException if a job is already running for this user
     */
    public UUID regenerateTimelineFromTimestampAsync(UUID userId, Instant earliestAffectedTimestamp) {
        return createAndRunAsyncJob(userId, jobId -> {
            log.info("Starting async partial timeline regeneration for job {} from {}", jobId, earliestAffectedTimestamp);
            executeRegenerationFromTimestampWithTransaction(userId, earliestAffectedTimestamp, jobId);
            finalizeJobAfterTimelineGeneration(jobId);
            log.info("Async partial timeline regeneration completed for job {}", jobId);
        });
    }

    public TimelineSchedulingResult scheduleTimelineRegenerationFromTimestamp(UUID userId, Instant earliestAffectedTimestamp) {
        if (earliestAffectedTimestamp == null) {
            return new TimelineSchedulingResult(null, false, false);
        }

        if (jobProgressService.getUserActiveJob(userId).isPresent()) {
            queuePendingRegeneration(userId, earliestAffectedTimestamp);
            return new TimelineSchedulingResult(null, true, true);
        }

        try {
            UUID jobId = regenerateTimelineFromTimestampAsync(userId, earliestAffectedTimestamp);
            return new TimelineSchedulingResult(jobId, true, false);
        } catch (IllegalStateException e) {
            queuePendingRegeneration(userId, earliestAffectedTimestamp);
            return new TimelineSchedulingResult(null, true, true);
        }
    }

    private UUID createAndRunAsyncJob(UUID userId, TimelineJobRunner runner) {
        var existingJob = jobProgressService.getUserActiveJob(userId);
        if (existingJob.isPresent()) {
            UUID existingJobId = existingJob.get().getJobId();
            log.warn("User {} already has an active timeline generation job: {}", userId, existingJobId);
            throw new IllegalStateException("A timeline generation job is already running. Job ID: " + existingJobId);
        }

        UUID jobId = jobProgressService.createJob(userId);
        log.info("Created async timeline generation job {} for user {}", jobId, userId);

        CompletableFuture.runAsync(() -> {
            try {
                runner.run(jobId);
            } catch (Exception e) {
                log.error("Async timeline generation failed for job {}: {}", jobId, e.getMessage(), e);
                try {
                    jobProgressService.failJob(jobId, "Unexpected error: " + e.getMessage());
                } catch (Exception failError) {
                    log.error("Failed to mark job {} as failed: {}", jobId, failError.getMessage());
                }
            } finally {
                drainPendingRegeneration(userId);
            }
        }, executorService);

        return jobId;
    }

    @Scheduled(every = "2s")
    void drainPendingRegenerations() {
        for (UUID userId : pendingRegenerationStartTimes.keySet()) {
            drainPendingRegeneration(userId);
        }
    }

    private void queuePendingRegeneration(UUID userId, Instant earliestAffectedTimestamp) {
        pendingRegenerationStartTimes.merge(userId, earliestAffectedTimestamp,
                (existing, requested) -> requested.isBefore(existing) ? requested : existing);
        log.info("Queued pending timeline regeneration for user {} from timestamp {}", userId, earliestAffectedTimestamp);
    }

    private void drainPendingRegeneration(UUID userId) {
        Instant pendingStart = pendingRegenerationStartTimes.get(userId);
        if (pendingStart == null || jobProgressService.getUserActiveJob(userId).isPresent()) {
            return;
        }

        if (!pendingRegenerationStartTimes.remove(userId, pendingStart)) {
            return;
        }

        try {
            UUID jobId = regenerateTimelineFromTimestampAsync(userId, pendingStart);
            log.info("Started queued timeline regeneration job {} for user {} from timestamp {}", jobId, userId, pendingStart);
        } catch (IllegalStateException e) {
            queuePendingRegeneration(userId, pendingStart);
        }
    }

    private void finalizeJobAfterTimelineGeneration(UUID jobId) {
        var currentStatus = jobProgressService.getJobProgress(jobId);
        if (currentStatus.isPresent() && currentStatus.get().isTerminal()) {
            return;
        }

        jobProgressService.updateProgress(jobId, "Timeline generation completed", 9, 100, null);
        jobProgressService.completeJob(jobId);
    }

    /**
     * Execute the regeneration in a new transaction context.
     * This is necessary because the async execution runs outside the original request transaction.
     */
    @Transactional
    protected void executeRegenerationWithTransaction(UUID userId, UUID jobId) {
        timelineGenerationService.generateTimelineFromTimestamp(userId, StreamingTimelineGenerationService.DEFAULT_START_DATE, jobId);
    }

    @Transactional
    protected void executeRegenerationFromTimestampWithTransaction(UUID userId, Instant earliestAffectedTimestamp, UUID jobId) {
        timelineGenerationService.generateTimelineFromTimestamp(userId, earliestAffectedTimestamp, jobId);
    }

    @FunctionalInterface
    private interface TimelineJobRunner {
        void run(UUID jobId);
    }
}
