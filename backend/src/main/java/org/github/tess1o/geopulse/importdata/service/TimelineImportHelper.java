package org.github.tess1o.geopulse.importdata.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.streaming.exception.TimelineGenerationLockException;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;

import java.time.Instant;


/**
 * Helper service for handling timeline generation after bulk imports.
 * Uses the new simplified background service with priority queues.
 */
@ApplicationScoped
@Slf4j
public class TimelineImportHelper {

    @Inject
    StreamingTimelineGenerationService timelineGenerationService;

    /**
     * Trigger timeline generation for imported GPS data.
     * Implements retry mechanism to handle concurrent timeline generation from RealTimeTimelineJob.
     */
    public void triggerTimelineGenerationForImportedGpsData(ImportJob job, Instant firstGpsPointDate) {
        log.info("Triggering timeline generation after bulk import for user {}", job.getUserId());

        int maxRetries = 10;
        int baseDelayMs = 1000; // Start with 1 second
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                timelineGenerationService.generateTimelineFromTimestamp(job.getUserId(), firstGpsPointDate);
                log.info("Successfully regenerated timeline for user {} after bulk import starting from date {} (attempt {})",
                        job.getUserId(), firstGpsPointDate, attempt);
                return; // Success - exit retry loop

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
                        return;
                    }
                } else {
                    log.error("Failed to trigger timeline generation for bulk import for user {} after {} attempts: {}",
                            job.getUserId(), maxRetries, e.getMessage());
                }
            } catch (Exception e) {
                // Other types of exceptions - don't retry
                log.error("Failed to trigger timeline generation for bulk import for user {}: {}",
                        job.getUserId(), e.getMessage(), e);
                break;
            }
        }
        
        // Don't fail the entire import - GPS data is still imported successfully
        log.warn("Timeline generation after import failed for user {} but GPS data import completed successfully", 
                job.getUserId());
    }
}