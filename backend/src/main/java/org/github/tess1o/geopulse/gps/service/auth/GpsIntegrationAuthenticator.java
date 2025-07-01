package org.github.tess1o.geopulse.gps.service.auth;

import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.util.Optional;
import java.util.UUID;

/**
 * Interface for GPS integration authentication strategies.
 * Each GPS integration type (OwnTracks, Overland, etc.) should have its own implementation.
 */
public interface GpsIntegrationAuthenticator {
    
    /**
     * Authenticate a GPS integration request based on the provided auth header.
     * 
     * @param authHeader The authorization header from the HTTP request
     * @return Optional containing the authenticated user ID, or empty if authentication failed
     */
    Optional<UUID> authenticate(String authHeader);
    
    /**
     * Get the GPS source type this authenticator handles.
     * 
     * @return The GPS source type
     */
    GpsSourceType getSupportedSourceType();
}