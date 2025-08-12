package org.github.tess1o.geopulse.gps.service.auth;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.exceptions.InvalidPasswordException;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.util.Optional;

@Slf4j
@ApplicationScoped
public class DawarichAuthenticator extends AbstractGpsIntegrationAuthenticator {

    @Override
    public GpsSourceType getSupportedSourceType() {
        return GpsSourceType.DAWARICH;
    }

    @Override
    protected Optional<GpsSourceConfigEntity> findConfig(String value) {
        log.info("Received value: {}", value);
        String apiKey = getApiKey(value);
        return configProvider.findByToken(apiKey);
    }

    @Override
    protected void validateCredentials(String value, GpsSourceConfigEntity config) throws InvalidPasswordException {
        String apiKey = getApiKey(value);
        if (!apiKey.equals(config.getToken())) {
            throw new InvalidPasswordException("Invalid api_key");
        }
    }

    @Override
    protected void logConfigNotFound() {
        log.error("No GPS source config found for Dawarich api_key");
    }

    @Override
    protected void logAuthenticationFailed(Exception e) {
        log.error("Dawarich authentication failed", e);
    }

    private String getApiKey(String value) {
        String apiKey;
        try {
            apiKey = extractBearerToken(value);
        } catch (IllegalArgumentException e) {
            apiKey = value;
        }
        return apiKey;
    }
}