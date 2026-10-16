package org.github.tess1o.geopulse.streaming.jobs;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.admin.service.BackupMaintenanceService;
import org.github.tess1o.geopulse.streaming.service.TimelineSafetyNetService;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Once a night, rebuilds the last {@code window-hours} of every recently active timeline and reports what changed
 * (see {@link TimelineSafetyNetService}). Users are handled one at a time to keep the load low.
 */
@ApplicationScoped
@Slf4j
public class TimelineSafetyNetJob {

    @Inject
    TimelineSafetyNetService safetyNetService;

    @Inject
    BackupMaintenanceService backupMaintenanceService;

    @ConfigProperty(name = "geopulse.timeline.safety-net.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "geopulse.timeline.safety-net.window-hours", defaultValue = "48")
    int windowHours;

    @Blocking
    @Scheduled(cron = "${geopulse.timeline.safety-net.cron:0 30 3 * * ?}", identity = "timeline-safety-net")
    void runScheduled() {
        if (!enabled) {
            return;
        }
        if (backupMaintenanceService != null && backupMaintenanceService.isRestoreBlocked()) {
            log.info("Skipping the timeline safety net while full backup restore is running");
            return;
        }
        run(Instant.now());
    }

    /** One run of the safety net as of {@code now}. */
    public Map<TimelineSafetyNetService.Outcome, Integer> run(Instant now) {
        Instant from = now.minus(Duration.ofHours(windowHours));
        List<UUID> users = safetyNetService.usersWithPointsSince(from);
        Map<TimelineSafetyNetService.Outcome, Integer> outcomes = new EnumMap<>(TimelineSafetyNetService.Outcome.class);
        int failed = 0;
        for (UUID userId : users) {
            try {
                outcomes.merge(safetyNetService.verifyUser(userId, from), 1, Integer::sum);
            } catch (Exception e) {
                failed++;
                log.error("Timeline safety net failed for user {}: {}", userId, e.getMessage(), e);
            }
        }
        log.info("Timeline safety net checked {} users from {}: {} (failed: {})", users.size(), from, outcomes, failed);
        return outcomes;
    }
}
