package org.github.tess1o.geopulse.gps.integrations.owntracks.mqtt;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Configuration class for conditional MQTT support.
 * Only activates MQTT functionality when GEOPULSE_MQTT_BROKER_HOST is configured.
 */
@ApplicationScoped
@Slf4j
@Getter
public class MqttConfiguration {

    private final boolean mqttEnabled;
    private final String brokerHost;
    private final int brokerPort;
    private final String username;
    private final String password;

    public MqttConfiguration() {
        boolean explicitlyEnabled = ConfigProvider.getConfig()
                .getOptionalValue("GEOPULSE_MQTT_ENABLED", Boolean.class)
                .orElse(false);
        
        this.brokerHost = ConfigProvider.getConfig()
                .getOptionalValue("GEOPULSE_MQTT_BROKER_HOST", String.class)
                .orElse("");
        
        this.brokerPort = ConfigProvider.getConfig()
                .getOptionalValue("GEOPULSE_MQTT_BROKER_PORT", Integer.class)
                .orElse(1883);
        
        this.username = ConfigProvider.getConfig()
                .getOptionalValue("GEOPULSE_MQTT_USERNAME", String.class)
                .orElse("");
        
        this.password = ConfigProvider.getConfig()
                .getOptionalValue("GEOPULSE_MQTT_PASSWORD", String.class)
                .orElse("");
        
        // Validate configuration when MQTT is enabled
        if (explicitlyEnabled) {
            if (brokerHost.trim().isEmpty()) {
                log.error("MQTT is enabled but GEOPULSE_MQTT_BROKER_HOST is not configured");
                this.mqttEnabled = false;
            } else {
                this.mqttEnabled = true;
                log.info("MQTT support is enabled - broker: {}:{}", brokerHost, brokerPort);
            }
        } else {
            this.mqttEnabled = false;
            log.debug("MQTT support is disabled - GEOPULSE_MQTT_ENABLED not set to true");
        }
    }

    /**
     * Get MQTT broker URL for connection
     */
    public String getBrokerUrl() {
        return "tcp://" + brokerHost + ":" + brokerPort;
    }

    /**
     * Check if authentication is required
     */
    public boolean hasCredentials() {
        return !username.trim().isEmpty() && !password.trim().isEmpty();
    }
}