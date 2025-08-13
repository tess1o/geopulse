package org.github.tess1o.geopulse.gps.integrations.owntracks.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.gps.service.auth.GpsIntegrationAuthenticatorRegistry;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * MQTT service for OwnTracks integration.
 * Only connects to MQTT when GEOPULSE_MQTT_BROKER_HOST is configured.
 */
@ApplicationScoped
@Slf4j
public class OwnTracksMqttService {

    private static final String CLIENT_ID = "geopulse-owntracks";
    private static final String TOPIC_PATTERN = "owntracks/+/+";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Inject
    MqttConfiguration mqttConfig;

    @Inject
    GpsPointService gpsPointService;

    @Inject
    GpsIntegrationAuthenticatorRegistry authRegistry;

    private MqttClient mqttClient;

    /**
     * Initialize MQTT connection on startup if enabled
     */
    void onStart(@Observes StartupEvent ev) {
        if (!mqttConfig.isMqttEnabled()) {
            log.info("MQTT support is disabled - skipping MQTT client initialization");
            return;
        }

        try {
            initializeMqttClient();
            log.info("OwnTracks MQTT service started successfully");
        } catch (Exception e) {
            log.error("Failed to start OwnTracks MQTT service", e);
        }
    }

    /**
     * Disconnect MQTT client on shutdown
     */
    void onStop(@Observes ShutdownEvent ev) {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                mqttClient.close();
                log.info("MQTT client disconnected");
            } catch (MqttException e) {
                log.warn("Error disconnecting MQTT client", e);
            }
        }
    }

    private void initializeMqttClient() throws MqttException {
        log.info("Initializing MQTT client for broker: {}", mqttConfig.getBrokerUrl());

        mqttClient = new MqttClient(mqttConfig.getBrokerUrl(), CLIENT_ID);
        
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setConnectionTimeout(30);
        options.setKeepAliveInterval(60);
        options.setAutomaticReconnect(true);

        if (mqttConfig.hasCredentials()) {
            options.setUserName(mqttConfig.getUsername());
            options.setPassword(mqttConfig.getPassword().toCharArray());
            log.debug("MQTT authentication configured for user: {}", mqttConfig.getUsername());
        }

        // Set callback for message handling
        mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                log.warn("MQTT connection lost", cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                handleMqttMessage(topic, new String(message.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Not used for incoming messages
            }
        });

        // Connect and subscribe
        mqttClient.connect(options);
        mqttClient.subscribe(TOPIC_PATTERN);
        
        log.info("MQTT client connected and subscribed to topic: {}", TOPIC_PATTERN);
    }

    /**
     * Handle incoming MQTT messages
     */
    private void handleMqttMessage(String topic, String payload) {
        try {
            log.debug("Received MQTT message on topic: {} - {}", topic, payload);

            // Parse topic: owntracks/{username}/{deviceId}
            String[] topicParts = topic.split("/");
            if (topicParts.length != 3 || !"owntracks".equals(topicParts[0])) {
                log.warn("Invalid OwnTracks MQTT topic format: {}", topic);
                return;
            }

            String username = topicParts[1];
            String deviceId = topicParts[2];

            // Parse message payload
            Map<String, Object> messageData = OBJECT_MAPPER.readValue(payload, Map.class);

            // Skip non-location messages
            if (!"location".equals(messageData.get("_type"))) {
                log.trace("Skipping non-location message: {}", messageData.get("_type"));
                return;
            }

            // Authenticate user
            Optional<UUID> userIdOpt = authRegistry.authenticateByUsername(username, GpsSourceType.OWNTRACKS);
            if (userIdOpt.isEmpty()) {
                log.warn("Authentication failed for MQTT user: {}", username);
                return;
            }

            UUID userId = userIdOpt.get();
            OwnTracksLocationMessage locationMessage = OBJECT_MAPPER.convertValue(messageData, OwnTracksLocationMessage.class);

            // Save GPS point
            gpsPointService.saveOwnTracksGpsPoint(locationMessage, userId, deviceId, GpsSourceType.OWNTRACKS);

            log.info("Successfully processed MQTT location message for user: {}, device: {}", username, deviceId);

        } catch (Exception e) {
            log.error("Error processing MQTT message from topic: {} - {}", topic, payload, e);
        }
    }

    /**
     * Check if MQTT service is active
     */
    public boolean isActive() {
        return mqttConfig.isMqttEnabled() && mqttClient != null && mqttClient.isConnected();
    }
}