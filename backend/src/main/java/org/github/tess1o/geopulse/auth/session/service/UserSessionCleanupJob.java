package org.github.tess1o.geopulse.auth.session.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;

/**
 * Deletes sessions that ended (revoked or expired) longer ago than the retention period.
 * The delete is idempotent, so running it on several backend instances at once is harmless.
 */
@ApplicationScoped
@Slf4j
public class UserSessionCleanupJob {

    @Inject
    UserSessionService userSessionService;

    @ConfigProperty(name = "geopulse.auth.session.cleanup.retention", defaultValue = "7d")
    Duration retention;

    @Scheduled(every = "1h", delayed = "5m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void deleteEndedSessions() {
        long deleted = userSessionService.deleteEndedBefore(Instant.now().minus(retention));
        if (deleted > 0) {
            log.info("Deleted {} ended user session(s)", deleted);
        }
    }
}
