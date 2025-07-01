package org.github.tess1o.geopulse.gps.service.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Registry that manages GPS integration authenticators.
 * Automatically discovers and registers all available authenticators.
 */
@ApplicationScoped
@Slf4j
public class GpsIntegrationAuthenticatorRegistry {

    private final Map<GpsSourceType, GpsIntegrationAuthenticator> authenticators;

    @Inject
    public GpsIntegrationAuthenticatorRegistry(Instance<GpsIntegrationAuthenticator> authenticatorInstances) {
        this.authenticators = new HashMap<>();

        // Auto-discover and register all authenticator implementations
        for (GpsIntegrationAuthenticator authenticator : authenticatorInstances) {
            GpsSourceType sourceType = authenticator.getSupportedSourceType();
            authenticators.put(sourceType, authenticator);
            log.info("Registered GPS authenticator for source type: {}", sourceType);
        }

        log.info("GPS Integration Authenticator Registry initialized with {} authenticators",
                authenticators.size());
    }

    /**
     * Authenticate a GPS integration request for the specified source type.
     *
     * @param sourceType The GPS source type
     * @param authHeader The authorization header
     * @return Optional containing the authenticated user ID, or empty if authentication failed
     */
    public Optional<UUID> authenticate(GpsSourceType sourceType, String authHeader) {
        GpsIntegrationAuthenticator authenticator = authenticators.get(sourceType);
        if (authenticator == null) {
            log.error("No authenticator found for GPS source type: {}", sourceType);
            return Optional.empty();
        }

        return authenticator.authenticate(authHeader);
    }
}