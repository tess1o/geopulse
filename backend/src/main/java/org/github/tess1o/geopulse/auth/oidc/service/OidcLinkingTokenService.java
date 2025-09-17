package org.github.tess1o.geopulse.auth.oidc.service;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.oidc.dto.OidcUserInfo;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing temporary OIDC account linking tokens.
 * These tokens are used during the account linking verification process.
 */
@ApplicationScoped
@Slf4j
public class OidcLinkingTokenService {

    private final Map<String, LinkingTokenData> linkingTokens = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    
    // Token expires after 10 minutes
    private static final int TOKEN_EXPIRY_MINUTES = 10;

    /**
     * Generate a secure linking token for account linking verification.
     */
    public String generateLinkingToken(String email, String newProvider, String state, OidcUserInfo originalUserInfo) {
        // Generate secure random token
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        
        // Store token data with expiration
        LinkingTokenData tokenData = new LinkingTokenData(
            email, 
            newProvider, 
            state,
            originalUserInfo,
            Instant.now().plus(TOKEN_EXPIRY_MINUTES, ChronoUnit.MINUTES)
        );
        
        linkingTokens.put(token, tokenData);
        log.debug("Generated linking token for email: {} and provider: {}", email, newProvider);
        
        // Clean up expired tokens periodically
        cleanupExpiredTokens();
        
        return token;
    }

    /**
     * Validate and consume a linking token.
     * Returns the token data if valid, null otherwise.
     * Token is consumed (removed) after successful validation.
     */
    public LinkingTokenData validateAndConsumeToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        
        LinkingTokenData tokenData = linkingTokens.remove(token);
        
        if (tokenData == null) {
            log.warn("Invalid or expired linking token attempted");
            return null;
        }
        
        if (tokenData.expiresAt().isBefore(Instant.now())) {
            log.warn("Expired linking token attempted for email: {}", tokenData.email());
            return null;
        }
        
        log.debug("Successfully validated linking token for email: {}", tokenData.email());
        return tokenData;
    }

    /**
     * Clean up expired tokens to prevent memory leaks.
     */
    private void cleanupExpiredTokens() {
        Instant now = Instant.now();
        int removedCount = 0;
        var iterator = linkingTokens.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().expiresAt().isBefore(now)) {
                iterator.remove();
                removedCount++;
            }
        }
        if (removedCount > 0) {
            log.debug("Cleaned up {} expired linking tokens", removedCount);
        }
    }

    /**
     * Public method for scheduled cleanup of expired tokens.
     * Called by the cleanup service to perform regular maintenance.
     */
    public void performScheduledCleanup() {
        cleanupExpiredTokens();
        log.debug("Performed scheduled cleanup of linking tokens. Current tokens in memory: {}", linkingTokens.size());
    }

    /**
     * Data stored with each linking token.
     */
    public record LinkingTokenData(
        String email,
        String newProvider,
        String originalState,
        OidcUserInfo originalUserInfo,
        Instant expiresAt
    ) {}
}