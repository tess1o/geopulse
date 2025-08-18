package org.github.tess1o.geopulse.gps.integrations.owntracks.mqtt;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Configuration class for conditional MQTT support.
 * Only activates MQTT functionality when geopulse.mqtt.enabled is set to true.
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

    @Inject
    public MqttConfiguration(
            @ConfigProperty(name = "geopulse.mqtt.enabled") boolean mqttEnabled,
            @ConfigProperty(name = "geopulse.mqtt.broker.host") String brokerHost,
            @ConfigProperty(name = "geopulse.mqtt.broker.port") int brokerPort,
            @ConfigProperty(name = "geopulse.mqtt.username") String username,
            @ConfigProperty(name = "geopulse.mqtt.password") String password) {
        
        this.brokerHost = brokerHost;
        this.brokerPort = brokerPort;
        this.username = username;
        this.password = password;
        
        // Validate configuration when MQTT is enabled
        if (mqttEnabled) {
            if (brokerHost.trim().isEmpty()) {
                log.error("MQTT is enabled but broker host is not configured");
                this.mqttEnabled = false;
            } else {
                this.mqttEnabled = true;
                log.info("MQTT support is enabled - broker: {}:{}", brokerHost, brokerPort);
            }
        } else {
            this.mqttEnabled = false;
            log.debug("MQTT support is disabled");
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