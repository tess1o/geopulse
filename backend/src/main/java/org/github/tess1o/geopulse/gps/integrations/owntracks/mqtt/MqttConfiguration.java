package org.github.tess1o.geopulse.gps.integrations.owntracks.mqtt;

import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.paho.client.mqttv3.internal.NetworkModuleService;

import java.net.URI;

/**
 * Configuration class for conditional MQTT support.
 * Only activates MQTT functionality when geopulse.mqtt.enabled is set to true.
 */
@ApplicationScoped
@Slf4j
@Getter
public class MqttConfiguration {

    @Inject
    @ConfigProperty(name = "geopulse.mqtt.enabled")
    @StaticInitSafe
    boolean mqttEnabledConfig;

    @Inject
    @ConfigProperty(name = "geopulse.mqtt.broker.host")
    @StaticInitSafe
    String brokerHost;

    @Inject
    @ConfigProperty(name = "geopulse.mqtt.broker.port")
    @StaticInitSafe
    int brokerPort;

    @Inject
    @ConfigProperty(name = "geopulse.mqtt.username")
    @StaticInitSafe
    String username;

    @Inject
    @ConfigProperty(name = "geopulse.mqtt.password")
    @StaticInitSafe
    String password;

    private boolean mqttEnabled;

    @PostConstruct
    void init() {
        // Validate configuration when MQTT is enabled
        if (mqttEnabledConfig) {
            if (brokerHost == null || brokerHost.trim().isEmpty()) {
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