package org.github.tess1o.geopulse.auth.oidc.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.oidc.model.OidcSessionStateEntity;
import org.github.tess1o.geopulse.auth.oidc.repository.OidcSessionStateRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for OidcCleanupService functionality.
 */
@QuarkusTest
public class OidcCleanupServiceTest {

    @Inject
    OidcCleanupService cleanupService;

    @Inject
    OidcSessionStateRepository sessionStateRepository;

    @Test
    @Transactional
    public void testCleanupExpiredSessionStates() {
        // Create test data - expired session state
        OidcSessionStateEntity expiredSession = OidcSessionStateEntity.builder()
                .stateToken("expired-token-123")
                .nonce("test-nonce")
                .providerName("google")
                .redirectUri("/app/timeline")
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS)) // Expired 1 hour ago
                .build();

        // Create test data - valid session state
        OidcSessionStateEntity validSession = OidcSessionStateEntity.builder()
                .stateToken("valid-token-456")
                .nonce("test-nonce-2")
                .providerName("microsoft")
                .redirectUri("/app/timeline")
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS)) // Expires in 1 hour
                .build();

        sessionStateRepository.persist(expiredSession);
        sessionStateRepository.persist(validSession);

        // Verify initial count
        long initialCount = sessionStateRepository.count();
        assertTrue(initialCount >= 2, "Should have at least 2 session states");

        // Run cleanup
        cleanupService.cleanupExpiredSessionStates();

        // Verify expired session was removed
        assertFalse(sessionStateRepository.findByStateToken("expired-token-123").isPresent(),
                "Expired session should be removed");

        // Verify valid session still exists
        assertTrue(sessionStateRepository.findByStateToken("valid-token-456").isPresent(),
                "Valid session should still exist");

        // Cleanup test data
        sessionStateRepository.delete(validSession);
    }

    @Test
    @Transactional
    public void testCleanupOldSessionStates() {
        // Create test data - very old session state (older than 24 hours)
        OidcSessionStateEntity oldSession = OidcSessionStateEntity.builder()
                .stateToken("old-token-789")
                .nonce("test-nonce-old")
                .providerName("okta")
                .redirectUri("/app/timeline")
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS)) // Still valid
                .build();

        // Manually set created_at to 25 hours ago (simulating old data)
        sessionStateRepository.persist(oldSession);
        
        // Update the created_at timestamp directly
        sessionStateRepository.getEntityManager()
                .createQuery("UPDATE OidcSessionStateEntity o SET o.createdAt = :oldDate WHERE o.id = :id")
                .setParameter("oldDate", Instant.now().minus(25, ChronoUnit.HOURS))
                .setParameter("id", oldSession.getId())
                .executeUpdate();

        // Run old session cleanup
        cleanupService.cleanupOldSessionStates();

        // Verify old session was removed
        assertFalse(sessionStateRepository.findByStateToken("old-token-789").isPresent(),
                "Old session should be removed");
    }

    @Test
    public void testCleanupLinkingTokens() {
        // This test just verifies the method runs without error
        assertDoesNotThrow(() -> cleanupService.cleanupExpiredLinkingTokens(),
                "Linking token cleanup should not throw exceptions");
    }
}