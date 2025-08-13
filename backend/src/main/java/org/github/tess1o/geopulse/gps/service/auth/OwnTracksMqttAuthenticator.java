package org.github.tess1o.geopulse.gps.service.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.exceptions.InvalidPasswordException;
import org.github.tess1o.geopulse.gps.integrations.owntracks.mqtt.MqttConfiguration;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.util.Optional;
import java.util.UUID;

/**
 * MQTT-specific authenticator for OwnTracks GPS integration.
 * Uses username-only authentication as MQTT security is handled at broker level.
 * Only active when GEOPULSE_MQTT_BROKER_HOST environment variable is set.
 */
@ApplicationScoped
@Slf4j
public class OwnTracksMqttAuthenticator extends AbstractGpsIntegrationAuthenticator {

    @Inject
    MqttConfiguration mqttConfiguration;

    @Override
    public GpsSourceType getSupportedSourceType() {
        return GpsSourceType.OWNTRACKS;
    }
    
    @Override
    public GpsSourceConfigEntity.ConnectionType getConnectionType() {
        return GpsSourceConfigEntity.ConnectionType.MQTT;
    }

    @Override
    protected Optional<GpsSourceConfigEntity> findConfig(String authHeader) {
        // This method is not used for MQTT authentication
        // MQTT uses authenticateByUsername instead
        throw new UnsupportedOperationException("MQTT authentication should use authenticateByUsername method");
    }

    @Override
    protected void validateCredentials(String authHeader, GpsSourceConfigEntity config) throws InvalidPasswordException {
        // No password validation for MQTT - assumes broker-level security
        log.debug("MQTT authentication successful for user: {}", config.getUsername());
    }

    @Override
    protected void logConfigNotFound() {
        log.error("No MQTT GPS source config found for user");
    }

    @Override
    protected void logAuthenticationFailed(Exception e) {
        log.error("OwnTracks MQTT authentication failed", e);
    }

    /**
     * MQTT-specific authentication method using username only.
     * Only works if MQTT is enabled via GEOPULSE_MQTT_BROKER_URL environment variable.
     *
     * @param username The username extracted from MQTT topic
     * @return Optional containing the authenticated user ID, or empty if authentication failed
     */
    @Override
    @Transactional
    public Optional<UUID> authenticateByUsername(String username) {
        // Check if MQTT is enabled
        if (!isMqttEnabled()) {
            log.debug("MQTT is not enabled - GEOPULSE_MQTT_BROKER_URL not configured");
            return Optional.empty();
        }

        try {
            Optional<GpsSourceConfigEntity> configOpt = configProvider.findByUsernameAndConnectionType(
                    username, GpsSourceConfigEntity.ConnectionType.MQTT);
            
            if (configOpt.isEmpty()) {
                log.warn("No MQTT GPS source config found for username: {}", username);
                return Optional.empty();
            }

            GpsSourceConfigEntity config = configOpt.get();
            if (!isConfigActive(config)) {
                log.warn("Inactive GPS source config for username: {}", username);
                return Optional.empty();
            }

            // No password validation needed for MQTT
            log.debug("MQTT authentication successful for username: {}", username);
            return Optional.of(config.getUser().getId());

        } catch (Exception e) {
            logAuthenticationFailed(e);
            return Optional.empty();
        }
    }

    /**
     * Check if MQTT support is enabled via environment variable.
     *
     * @return true if GEOPULSE_MQTT_BROKER_HOST is configured
     */
    private boolean isMqttEnabled() {
        return mqttConfiguration.isMqttEnabled();
    }
}