package org.github.tess1o.geopulse.gps.service.auth;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.exceptions.InvalidPasswordException;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.util.Optional;

@ApplicationScoped
@Slf4j
public class HomeAssistantAuthenticator extends AbstractGpsIntegrationAuthenticator {
    @Override
    public GpsSourceType getSupportedSourceType() {
        return GpsSourceType.HOME_ASSISTANT;
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
        log.error("No GPS source config found for Home Assisant token");
    }

    @Override
    protected void logAuthenticationFailed(Exception e) {
        log.error("Home Assisant authentication failed", e);
    }
}
