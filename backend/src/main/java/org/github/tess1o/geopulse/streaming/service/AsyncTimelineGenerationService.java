package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.util.UUID;

/**
 * Service for executing timeline generation asynchronously.
 * This allows the REST endpoint to return immediately with a job ID
 * while the timeline generation runs in the background.
 */
@ApplicationScoped
@Slf4j
public class AsyncTimelineGenerationService {

    @Inject
    ManagedExecutor managedExecutorService;

    @Inject
    StreamingTimelineGenerationService timelineGenerationService;

    @Inject
    TimelineJobProgressService jobProgressService;

    /**
     * Start timeline regeneration asynchronously.
     * Creates a job, returns the job ID immediately, and runs the generation in the background.
     *
     * @param userId The user ID
     * @return The job ID for tracking progress
     * @throws IllegalStateException if a job is already running for this user
     */
    public UUID regenerateTimelineAsync(UUID userId) {
        // Check if user already has an active job
        var existingJob = jobProgressService.getUserActiveJob(userId);
        if (existingJob.isPresent()) {
            UUID existingJobId = existingJob.get().getJobId();
            log.warn("User {} already has an active timeline generation job: {}", userId, existingJobId);
            throw new IllegalStateException("A timeline generation job is already running. Job ID: " + existingJobId);
        }

        // Create job upfront
        UUID jobId = jobProgressService.createJob(userId);

        log.info("Created async timeline generation job {} for user {}", jobId, userId);

        managedExecutorService.runAsync(() -> {
            try {
                log.info("Starting async timeline generation for job {}", jobId);
                executeRegenerationWithTransaction(userId, jobId);
                log.info("Async timeline generation completed for job {}", jobId);
            } catch (Exception e) {
                log.error("Async timeline generation failed for job {}: {}", jobId, e.getMessage(), e);
                // Error handling is done within the service, but ensure job is marked as failed
                try {
                    jobProgressService.failJob(jobId, "Unexpected error: " + e.getMessage());
                } catch (Exception failError) {
                    log.error("Failed to mark job {} as failed: {}", jobId, failError.getMessage());
                }
            }
        });

        return jobId;
    }

    /**
     * Execute the regeneration in a new transaction context.
     * This is necessary because the async execution runs outside the original request transaction.
     */
    @Transactional
    protected void executeRegenerationWithTransaction(UUID userId, UUID jobId) {
        timelineGenerationService.regenerateFullTimeline(userId, jobId);
    }
}
