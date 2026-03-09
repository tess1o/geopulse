package org.github.tess1o.geopulse.gps.service.auth;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.exceptions.InvalidPasswordException;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.util.Optional;

@ApplicationScoped
@Slf4j
public class TraccarAuthenticator extends AbstractGpsIntegrationAuthenticator {

    @Override
    public GpsSourceType getSupportedSourceType() {
        return GpsSourceType.TRACCAR;
    }

    @Override
    protected Optional<GpsSourceConfigEntity> findConfig(String authHeader) {
        String token = extractBearerToken(authHeader);
        return configProvider.findByTokenAndSourceType(token, GpsSourceType.TRACCAR);
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
        log.error("No GPS source config found for Traccar token");
    }

    @Override
    protected void logAuthenticationFailed(Exception e) {
        log.error("Traccar authentication failed", e);
    }
}
