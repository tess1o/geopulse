package org.github.tess1o.geopulse.gps.service.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
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

    private final Map<GpsSourceType, Map<GpsSourceConfigEntity.ConnectionType, GpsIntegrationAuthenticator>> authenticators;

    @Inject
    public GpsIntegrationAuthenticatorRegistry(Instance<GpsIntegrationAuthenticator> authenticatorInstances) {
        this.authenticators = new HashMap<>();

        // Auto-discover and register all authenticator implementations
        for (GpsIntegrationAuthenticator authenticator : authenticatorInstances) {
            GpsSourceType sourceType = authenticator.getSupportedSourceType();
            GpsSourceConfigEntity.ConnectionType connectionType = authenticator.getConnectionType();
            
            // Create nested map if it doesn't exist for this source type
            authenticators.computeIfAbsent(sourceType, _ -> new HashMap<>());
            
            // Register authenticator by source type and connection type
            authenticators.get(sourceType).put(connectionType, authenticator);
            
            log.info("Registered GPS authenticator for source type: {} with connection type: {}", 
                    sourceType, connectionType);
        }

        int totalAuthenticators = authenticators.values().stream()
                .mapToInt(Map::size)
                .sum();
        
        log.info("GPS Integration Authenticator Registry initialized with {} authenticators", totalAuthenticators);
    }

    /**
     * Authenticate a GPS integration request for the specified source type.
     *
     * @param sourceType The GPS source type
     * @param authHeader The authorization header
     * @return Optional containing the authenticated user ID, or empty if authentication failed
     */
    public Optional<UUID> authenticate(GpsSourceType sourceType, String authHeader) {
        Map<GpsSourceConfigEntity.ConnectionType, GpsIntegrationAuthenticator> sourceAuthenticators = 
                authenticators.get(sourceType);
        
        if (sourceAuthenticators == null) {
            log.error("No authenticators found for GPS source type: {}", sourceType);
            return Optional.empty();
        }
        
        // For HTTP-based authentication, look for HTTP authenticator
        GpsIntegrationAuthenticator authenticator = sourceAuthenticators.get(GpsSourceConfigEntity.ConnectionType.HTTP);
        if (authenticator == null) {
            log.error("No HTTP authenticator found for GPS source type: {}", sourceType);
            return Optional.empty();
        }

        return authenticator.authenticate(authHeader);
    }

    /**
     * Authenticate a GPS integration request using username only (for MQTT).
     *
     * @param username The username extracted from MQTT topic
     * @param sourceType The GPS source type
     * @return Optional containing the authenticated user ID, or empty if authentication failed
     */
    public Optional<UUID> authenticateByUsername(String username, GpsSourceType sourceType) {
        Map<GpsSourceConfigEntity.ConnectionType, GpsIntegrationAuthenticator> sourceAuthenticators = 
                authenticators.get(sourceType);
        
        if (sourceAuthenticators == null) {
            log.error("No authenticators found for GPS source type: {}", sourceType);
            return Optional.empty();
        }
        
        // For MQTT-based authentication, look for MQTT authenticator
        GpsIntegrationAuthenticator authenticator = sourceAuthenticators.get(GpsSourceConfigEntity.ConnectionType.MQTT);
        if (authenticator == null) {
            log.error("No MQTT authenticator found for GPS source type: {}", sourceType);
            return Optional.empty();
        }

        log.debug("Using MQTT authenticator for username-based authentication: {}", username);
        return authenticator.authenticateByUsername(username);
    }
}