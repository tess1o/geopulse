package org.github.tess1o.geopulse.gps.service.auth;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.exceptions.InvalidPasswordException;
import org.github.tess1o.geopulse.gps.model.GpsAuthenticationResult;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.gpssource.service.GpsSourceConfigProvider;

import java.util.Optional;

/**
 * Abstract base class for GPS integration authenticators.
 * Provides common functionality like config validation and error handling.
 */
@Slf4j
public abstract class AbstractGpsIntegrationAuthenticator implements GpsIntegrationAuthenticator {

    @Inject
    protected GpsSourceConfigProvider configProvider;

    @Override
    public final Optional<GpsAuthenticationResult> authenticate(String authHeader) {
        try {
            Optional<GpsSourceConfigEntity> configOpt = findConfig(authHeader);
            if (configOpt.isEmpty()) {
                logConfigNotFound();
                return Optional.empty();
            }

            GpsSourceConfigEntity config = configOpt.get();
            if (!isConfigActive(config)) {
                return Optional.empty();
            }

            validateCredentials(authHeader, config);

            // Return both userId and the full config for downstream filtering
            // This avoids a second DB lookup - we already have the config loaded
            return Optional.of(new GpsAuthenticationResult(
                    config.getUser().getId(),
                    config
            ));

        } catch (InvalidPasswordException | IllegalArgumentException e) {
            logAuthenticationFailed(e);
            return Optional.empty();
        }
    }
    
    /**
     * Find the GPS source configuration based on the auth header.
     * Each implementation should define how to extract identifiers from the auth header.
     * 
     * @param authHeader The authorization header
     * @return Optional containing the config if found
     */
    protected abstract Optional<GpsSourceConfigEntity> findConfig(String authHeader);
    
    /**
     * Validate the credentials against the configuration.
     * Each implementation should define its specific validation logic.
     * 
     * @param authHeader The authorization header
     * @param config The GPS source configuration
     * @throws InvalidPasswordException if credentials are invalid
     */
    protected abstract void validateCredentials(String authHeader, GpsSourceConfigEntity config) throws InvalidPasswordException;
    
    /**
     * Log message when configuration is not found.
     */
    protected abstract void logConfigNotFound();
    
    /**
     * Log message when authentication fails.
     * 
     * @param e The exception that caused the failure
     */
    protected abstract void logAuthenticationFailed(Exception e);
    
    /**
     * Check if the GPS source configuration is active.
     * 
     * @param config The GPS source configuration
     * @return true if active, false otherwise
     */
    protected boolean isConfigActive(GpsSourceConfigEntity config) {
        if (!config.isActive()) {
            log.error("GPS Source Config {} is not active", config.getId());
            return false;
        }
        return true;
    }

    protected String extractBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Invalid Bearer Auth header");
        }
        return authHeader.substring("Bearer ".length());
    }
}