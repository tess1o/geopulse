package org.github.tess1o.geopulse.importdata.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
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
     * Uses low-priority queue since bulk imports can wait for system resources.
     */
    public void triggerTimelineGenerationForImportedGpsData(ImportJob job, Instant firstGpsPointDate) {
        log.info("Triggering timeline generation after bulk import for user {}", job.getUserId());

        try {
            timelineGenerationService.generateTimelineFromTimestamp(job.getUserId(), firstGpsPointDate);
            log.info("Successfully regenerated timeline for user {} after bulk import starting from date {}",
                    job.getUserId(), firstGpsPointDate);

        } catch (Exception e) {
            log.error("Failed to trigger timeline generation for bulk import for user {}: {}",
                    job.getUserId(), e.getMessage(), e);
            // Don't fail the entire import - GPS data is still imported successfully
        }
    }
}