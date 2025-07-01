package org.github.tess1o.geopulse.gps.service.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.auth.exceptions.InvalidPasswordException;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.service.PasswordUtils;

import java.util.Optional;

/**
 * Authenticator for OwnTracks GPS integration.
 * Uses Basic Authentication with username/password.
 */
@ApplicationScoped
@Slf4j
public class OwnTracksAuthenticator extends AbstractGpsIntegrationAuthenticator {
    
    private final AuthenticationService authenticationService;
    private final PasswordUtils passwordUtils;
    
    @Inject
    public OwnTracksAuthenticator(AuthenticationService authenticationService, 
                                 PasswordUtils passwordUtils) {
        this.authenticationService = authenticationService;
        this.passwordUtils = passwordUtils;
    }
    
    @Override
    public GpsSourceType getSupportedSourceType() {
        return GpsSourceType.OWNTRACKS;
    }
    
    @Override
    protected Optional<GpsSourceConfigEntity> findConfig(String authHeader) {
        String[] authArray = authenticationService.extractUsernameAndPassword(authHeader);
        String username = authArray[0];
        return configProvider.findByUsername(username);
    }
    
    @Override
    protected void validateCredentials(String authHeader, GpsSourceConfigEntity config) throws InvalidPasswordException {
        String[] authArray = authenticationService.extractUsernameAndPassword(authHeader);
        String password = authArray[1];
        
        boolean isPasswordValid = passwordUtils.verifyPassword(password, config.getPasswordHash());
        if (!isPasswordValid) {
            throw new InvalidPasswordException("Invalid password for OwnTracks");
        }
    }
    
    @Override
    protected void logConfigNotFound() {
        log.error("No GPS source config found for OwnTracks user");
    }
    
    @Override
    protected void logAuthenticationFailed(Exception e) {
        log.error("OwnTracks authentication failed", e);
    }
}