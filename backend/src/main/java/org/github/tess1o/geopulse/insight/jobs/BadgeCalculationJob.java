package org.github.tess1o.geopulse.insight.jobs;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.insight.model.BadgeProcessingResult;
import org.github.tess1o.geopulse.insight.service.UserBadgeService;

import java.util.List;
import java.util.UUID;

/**
 * Scheduled job for processing user badges.
 * Simplified to use BadgeService for all badge operations.
 */
@ApplicationScoped
@Slf4j
public class BadgeCalculationJob {

    private final UserBadgeService userBadgeService;
    private final EntityManager entityManager;

    @ConfigProperty(name = "geopulse.badges.calculation.enabled", defaultValue = "true")
    @StaticInitSafe
    boolean badgeCalculationEnabled;

    public BadgeCalculationJob(UserBadgeService userBadgeService,
                               EntityManager entityManager) {
        this.userBadgeService = userBadgeService;
        this.entityManager = entityManager;
    }

    /**
     * Scheduled job to process badges for all users every 30 minutes.
     * Ensures all badge types exist for every user, then updates incomplete badges.
     */
    @Scheduled(every = "30m", delay = 0)
    public void processBadges() {
        if (!badgeCalculationEnabled) {
            log.debug("Badge calculation is disabled, skipping scheduled update");
            return;
        }

        log.info("Starting scheduled badge calculation for all users");
        
        try {
            List<UUID> activeUsers = getActiveUserIds();
            log.info("Processing badges for {} active users", activeUsers.size());

            BadgeProcessingResult totalResult = BadgeProcessingResult.EMPTY;

            for (UUID userId : activeUsers) {
                try {
                    BadgeProcessingResult result = processUserBadges(userId);
                    totalResult = totalResult.add(result);
                } catch (Exception e) {
                    log.error("Failed to process badges for user {}: {}", userId, e.getMessage(), e);
                    // Continue with next user - don't let one failure stop the whole job
                }
            }

            log.info("Scheduled badge calculation completed: {}", totalResult);
                    
        } catch (Exception e) {
            log.error("Failed to execute scheduled badge calculation: {}", e.getMessage(), e);
        }
    }

    /**
     * Process all badges for a single user.
     * Ensures all badge types exist and updates incomplete badges.
     */
    @Transactional
    public BadgeProcessingResult processUserBadges(UUID userId) {
        log.debug("Processing badges for user: {}", userId);
        return userBadgeService.processAllBadges(userId);
    }

    /**
     * Get all active user IDs for badge processing
     */
    private List<UUID> getActiveUserIds() {
        return entityManager
                .createQuery("SELECT u.id FROM UserEntity u WHERE u.isActive = true", UUID.class)
                .getResultList();
    }
}