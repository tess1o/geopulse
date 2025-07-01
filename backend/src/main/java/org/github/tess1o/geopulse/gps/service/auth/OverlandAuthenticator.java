package org.github.tess1o.geopulse.gps.service.auth;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.exceptions.InvalidPasswordException;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.util.Optional;

/**
 * Authenticator for Overland GPS integration.
 * Uses Bearer Token authentication.
 */
@ApplicationScoped
@Slf4j
public class OverlandAuthenticator extends AbstractGpsIntegrationAuthenticator {
    
    @Override
    public GpsSourceType getSupportedSourceType() {
        return GpsSourceType.OVERLAND;
    }
    
    @Override
    protected Optional<GpsSourceConfigEntity> findConfig(String authHeader) {
        String token = extractBearerToken(authHeader);
        return configProvider.findByToken(token);
    }
    
    @Override
    protected void validateCredentials(String authHeader, GpsSourceConfigEntity config) throws InvalidPasswordException {
        String token = extractBearerToken(authHeader);
        if (!token.equals(config.getToken())) {
            throw new InvalidPasswordException("Invalid token");
        }
    }
    
    @Override
    protected void logConfigNotFound() {
        log.error("No GPS source config found for Overland token");
    }
    
    @Override
    protected void logAuthenticationFailed(Exception e) {
        log.error("Overland authentication failed", e);
    }
    
    private String extractBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Invalid Bearer Auth header");
        }
        return authHeader.substring("Bearer ".length());
    }
}