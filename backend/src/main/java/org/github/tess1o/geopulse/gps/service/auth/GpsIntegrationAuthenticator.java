package org.github.tess1o.geopulse.gps.service.auth;

import org.github.tess1o.geopulse.gps.model.GpsAuthenticationResult;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.util.Optional;

/**
 * Interface for GPS integration authentication strategies.
 * Each GPS integration type (OwnTracks, Overland, etc.) should have its own implementation.
 */
public interface GpsIntegrationAuthenticator {

    /**
     * Authenticate a GPS integration request based on the provided auth header.
     *
     * @param authHeader The authorization header from the HTTP request
     * @return Optional containing the authentication result (userId and configId), or empty if authentication failed
     */
    Optional<GpsAuthenticationResult> authenticate(String authHeader);

    /**
     * Get the GPS source type this authenticator handles.
     *
     * @return The GPS source type
     */
    GpsSourceType getSupportedSourceType();

    /**
     * Get the connection type this authenticator supports.
     *
     * @return The connection type (HTTP, MQTT)
     */
    default GpsSourceConfigEntity.ConnectionType getConnectionType() {
        return GpsSourceConfigEntity.ConnectionType.HTTP;
    }

    /**
     * Authenticate a GPS integration request using username only (for MQTT).
     * Default implementation returns empty - only MQTT authenticators should override this.
     *
     * @param username The username extracted from MQTT topic
     * @return Optional containing the authentication result (userId and configId), or empty if authentication failed
     */
    default Optional<GpsAuthenticationResult> authenticateByUsername(String username) {
        return Optional.empty();
    }
}