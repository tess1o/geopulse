package org.github.tess1o.geopulse.streaming.service;

import io.quarkus.panache.common.Parameters;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.user.model.TimelineStatus;
import org.github.tess1o.geopulse.user.model.UserEntity;

/**
 * Service that recovers users stuck in timeline processing states after backend crashes/restarts.
 * Resets PROCESSING and REGENERATING statuses to IDLE on application startup to prevent permanent deadlocks.
 */
@ApplicationScoped
@Slf4j
public class TimelineStatusRecoveryService {

    /**
     * Reset stuck timeline statuses on application startup.
     * This handles cases where the backend crashed during timeline generation,
     * leaving users permanently in PROCESSING or REGENERATING state.
     */
    @Transactional
    public void onStartup(@Observes StartupEvent startupEvent) {
        log.info("Starting timeline status recovery...");
        
        try {
            // Reset all stuck statuses to IDLE
            int recoveredUsers = UserEntity.update(
                "timelineStatus = :idleStatus WHERE timelineStatus IN (:stuckStatuses)",
                Parameters.with("idleStatus", TimelineStatus.IDLE)
                          .and("stuckStatuses", java.util.List.of(TimelineStatus.PROCESSING, TimelineStatus.REGENERATING))
            );
            
            if (recoveredUsers > 0) {
                log.warn("Recovered {} users from stuck timeline processing states (PROCESSING/REGENERATING -> IDLE)", 
                        recoveredUsers);
            } else {
                log.info("No users found in stuck timeline processing states - all users are IDLE");
            }
            
        } catch (Exception e) {
            log.error("Failed to recover timeline statuses on startup: {}", e.getMessage(), e);
            // Don't throw - this shouldn't prevent application startup
        }
    }
}