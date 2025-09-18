package org.github.tess1o.geopulse.auth.oidc.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.auth.oidc.repository.OidcSessionStateRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * Service for cleaning up expired OIDC-related data.
 * This service runs scheduled tasks to prevent data accumulation and memory leaks.
 */
@ApplicationScoped
@Slf4j
public class OidcCleanupService {

    @Inject
    OidcSessionStateRepository sessionStateRepository;

    @Inject
    OidcLinkingTokenService linkingTokenService;

    @ConfigProperty(name = "geopulse.oidc.cleanup.session-states.enabled", defaultValue = "true")
    boolean sessionStatesCleanupEnabled;

    @ConfigProperty(name = "geopulse.oidc.cleanup.linking-tokens.enabled", defaultValue = "true")
    boolean linkingTokensCleanupEnabled;


    /**
     * Clean up expired OIDC session states.
     * Runs every 5 minutes to remove expired session states from the database.
     */
    @Scheduled(every = "5m")
    @Transactional
    public void cleanupExpiredSessionStates() {
        if (!sessionStatesCleanupEnabled) {
            return;
        }
        
        try {
            long deletedCount = sessionStateRepository.deleteExpired();
            if (deletedCount > 0) {
                log.info("Cleaned up {} expired OIDC session states", deletedCount);
            } else {
                log.debug("No expired OIDC session states to clean up");
            }
        } catch (Exception e) {
            log.error("Failed to clean up expired OIDC session states", e);
        }
    }

    /**
     * Clean up old OIDC session states (older than 24 hours).
     * Runs daily to remove very old session states that might have been missed.
     */
    @Scheduled(every = "24h", delay = 1, delayUnit = TimeUnit.HOURS)
    @Transactional
    public void cleanupOldSessionStates() {
        if (!sessionStatesCleanupEnabled) {
            return;
        }
        
        try {
            Instant cutoffTime = Instant.now().minus(24, ChronoUnit.HOURS);
            long deletedCount = sessionStateRepository.deleteOlderThan(cutoffTime);
            if (deletedCount > 0) {
                log.info("Cleaned up {} old OIDC session states (older than 24h)", deletedCount);
            } else {
                log.debug("No old OIDC session states to clean up");
            }
        } catch (Exception e) {
            log.error("Failed to clean up old OIDC session states", e);
        }
    }

    /**
     * Clean up expired linking tokens from memory.
     * Runs every 10 minutes to prevent memory leaks in the token service.
     */
    @Scheduled(every = "10m", delay = 30, delayUnit = TimeUnit.MINUTES)
    public void cleanupExpiredLinkingTokens() {
        if (!linkingTokensCleanupEnabled) {
            return;
        }
        
        try {
            linkingTokenService.performScheduledCleanup();
            log.debug("Performed scheduled cleanup of expired linking tokens");
        } catch (Exception e) {
            log.error("Failed to clean up expired linking tokens", e);
        }
    }
}