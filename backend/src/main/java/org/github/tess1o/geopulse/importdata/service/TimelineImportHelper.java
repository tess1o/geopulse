package org.github.tess1o.geopulse.importdata.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.insight.service.BadgeRecalculationService;
import org.github.tess1o.geopulse.streaming.exception.TimelineGenerationLockException;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.TimelineJobProgressService;

import java.time.Instant;
import java.util.UUID;


/**
 * Helper service for handling timeline generation after bulk imports.
 * Uses the new simplified background service with priority queues.
 */
@ApplicationScoped
@Slf4j
public class TimelineImportHelper {

    @Inject
    StreamingTimelineGenerationService timelineGenerationService;

    @Inject
    TimelineJobProgressService jobProgressService;

    @Inject
    BadgeRecalculationService badgeRecalculationService;

    /**
     * Trigger timeline generation for imported GPS data with job tracking.
     * Implements retry mechanism to handle concurrent timeline generation from RealTimeTimelineJob.
     * IMPORTANT: Sets the timeline job ID on the import job IMMEDIATELY to avoid race conditions.
     *
     * @param job               The import job
     * @param firstGpsPointDate Starting timestamp for timeline regeneration
     * @return UUID of the created timeline job, or null if creation failed
     */
    public UUID triggerTimelineGenerationForImportedGpsData(ImportJob job, Instant firstGpsPointDate) {
        log.info("Triggering timeline generation after bulk import for user {}", job.getUserId());

        // Create timeline job for progress tracking
        UUID timelineJobId = jobProgressService.createJob(job.getUserId());
        log.info("Created timeline job {} for import {}", timelineJobId, job.getJobId());

        // CRITICAL: Set timeline job ID on import job IMMEDIATELY before triggering generation
        // This ensures frontend can see the timeline job ID even during the retry loop
        job.setTimelineJobId(timelineJobId);

        // Update import progress immediately so frontend sees the change
        job.updateProgress(75, "Timeline generation in progress...");

        int maxRetries = 10;
        int baseDelayMs = 1000; // Start with 1 second

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                timelineGenerationService.generateTimelineFromTimestamp(job.getUserId(), firstGpsPointDate, timelineJobId);
                log.info("Successfully regenerated timeline for user {} after bulk import starting from date {} (attempt {})",
                        job.getUserId(), firstGpsPointDate, attempt);

                // Complete the timeline job (must happen OUTSIDE the @Transactional method)
                finishTimelineJob(timelineJobId, job.getUserId());
                return timelineJobId; // Success - return job ID

            } catch (TimelineGenerationLockException e) {
                // Timeline generation lock conflict - retry with exponential backoff
                if (attempt < maxRetries) {
                    int delayMs = baseDelayMs * (1 << (attempt - 1)); // Exponential backoff: 1s, 2s, 4s, 8s, 16s, ...
                    log.info("Timeline generation already in progress for user {} - retrying in {}ms (attempt {}/{})",
                            job.getUserId(), delayMs, attempt, maxRetries);

                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Timeline generation retry interrupted for user {}", job.getUserId());
                        return timelineJobId; // Return job ID even if interrupted
                    }
                } else {
                    log.error("Failed to trigger timeline generation for bulk import for user {} after {} attempts: {}",
                            job.getUserId(), maxRetries, e.getMessage());
                    jobProgressService.failJob(timelineJobId, "Failed to acquire timeline lock after " + maxRetries + " retries");
                }
            } catch (Exception e) {
                // Other types of exceptions - don't retry
                log.error("Failed to trigger timeline generation for bulk import for user {}: {}",
                        job.getUserId(), e.getMessage(), e);
                jobProgressService.failJob(timelineJobId, "Timeline generation failed: " + e.getMessage());
                break;
            }
        }

        // Don't fail the entire import - GPS data is still imported successfully
        log.warn("Timeline generation after import failed for user {} but GPS data import completed successfully",
                job.getUserId());
        return timelineJobId; // Return job ID even if failed, so frontend can show the error
    }

    /**
     * Complete timeline job with badge recalculation and progress updates.
     * Must be called OUTSIDE @Transactional method to avoid transaction issues.
     */
    private void finishTimelineJob(UUID timelineJobId, UUID userId) {
        try {
            // Badge recalculation (99%)
            jobProgressService.updateProgress(timelineJobId, "Recalculating achievement badges", 9, 99, null);

            // Note: BadgeRecalculationService has its own @Transactional
            badgeRecalculationService.recalculateAllBadgesForUser(userId);
            log.info("Triggered badge recalculation for user {} after timeline import", userId);
        } catch (Exception e) {
            log.error("Failed to recalculate badges for user {} after timeline import: {}",
                    userId, e.getMessage(), e);
            // Don't fail the timeline job if badge calculation fails
        }

        // Mark as completed (100%)
        jobProgressService.updateProgress(timelineJobId, "Timeline generation completed", 9, 100, null);
        jobProgressService.completeJob(timelineJobId);
    }
}