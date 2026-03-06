package org.github.tess1o.geopulse.gps.service.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.auth.exceptions.InvalidPasswordException;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.service.SecurePasswordUtils;

import java.util.Optional;

/**
 * Authenticator for Colota GPS integration.
 * Uses Basic Authentication with username/password.
 */
@ApplicationScoped
@Slf4j
public class ColotaAuthenticator extends AbstractGpsIntegrationAuthenticator {

    private final AuthenticationService authenticationService;
    private final SecurePasswordUtils passwordUtils;

    @Inject
    public ColotaAuthenticator(AuthenticationService authenticationService,
                               SecurePasswordUtils passwordUtils) {
        this.authenticationService = authenticationService;
        this.passwordUtils = passwordUtils;
    }

    @Override
    public GpsSourceType getSupportedSourceType() {
        return GpsSourceType.COLOTA;
    }

    @Override
    protected Optional<GpsSourceConfigEntity> findConfig(String authHeader) {
        String[] authArray = authenticationService.extractUsernameAndPassword(authHeader);
        String username = authArray[0];
        return configProvider.findByUsernameAndSourceType(username, GpsSourceType.COLOTA);
    }

    @Override
    protected void validateCredentials(String authHeader, GpsSourceConfigEntity config) throws InvalidPasswordException {
        String[] authArray = authenticationService.extractUsernameAndPassword(authHeader);
        String password = authArray[1];

        boolean isPasswordValid = passwordUtils.isPasswordValid(password, config.getPasswordHash());
        if (!isPasswordValid) {
            throw new InvalidPasswordException("Invalid password for Colota");
        }
    }

    @Override
    protected void logConfigNotFound() {
        log.error("No GPS source config found for Colota user");
    }

    @Override
    protected void logAuthenticationFailed(Exception e) {
        log.error("Colota authentication failed", e);
    }
}
