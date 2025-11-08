package org.github.tess1o.geopulse.streaming.jobs;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.service.TimelineJobProgressService;

/**
 * Scheduled job that cleans up old completed timeline generation jobs from memory.
 * Runs every 15 minutes to remove jobs older than 1 hour.
 */
@ApplicationScoped
@Slf4j
public class TimelineJobCleanupScheduler {

    @Inject
    TimelineJobProgressService jobProgressService;

    /**
     * Clean up old timeline generation jobs.
     * Runs every 15 minutes.
     */
    @Scheduled(every = "15m", identity = "timeline-job-cleanup")
    public void cleanupOldJobs() {
        log.debug("Starting timeline job cleanup...");

        try {
            int removedCount = jobProgressService.cleanupOldJobs();

            if (removedCount > 0) {
                log.info("Timeline job cleanup completed: removed {} old jobs", removedCount);
            } else {
                log.debug("Timeline job cleanup completed: no old jobs to remove");
            }

            // Log statistics
            var stats = jobProgressService.getStatistics();
            log.debug("Timeline job statistics: {}", stats);

        } catch (Exception e) {
            log.error("Error during timeline job cleanup", e);
        }
    }
}
