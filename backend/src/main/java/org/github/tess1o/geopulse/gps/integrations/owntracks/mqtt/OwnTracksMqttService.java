package org.github.tess1o.geopulse.gps.integrations.owntracks.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.integrations.owntracks.service.OwnTracksPoiService;
import org.github.tess1o.geopulse.gps.integrations.owntracks.service.OwnTracksTagService;
import org.github.tess1o.geopulse.gps.model.GpsAuthenticationResult;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.gps.service.auth.GpsIntegrationAuthenticatorRegistry;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MQTT service for OwnTracks integration.
 * Only connects to MQTT when GEOPULSE_MQTT_BROKER_HOST is configured.
 */
@ApplicationScoped
@Slf4j
public class OwnTracksMqttService {

    @ConfigProperty(name = "geopulse.owntracks.ping.timestamp.override", defaultValue = "false")
    private boolean timestampOverride;

    private static final String CLIENT_ID = "geopulse-owntracks";
    private static final String TOPIC_PATTERN = "owntracks/+/+";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Inject
    MqttConfiguration mqttConfig;

    @Inject
    GpsPointService gpsPointService;

    @Inject
    GpsIntegrationAuthenticatorRegistry authRegistry;

    @Inject
    OwnTracksPoiService ownTracksPoiService;

    @Inject
    OwnTracksTagService ownTracksTagService;

    private MqttClient mqttClient;
    private MqttConnectOptions connectOptions;
    private ScheduledExecutorService reconnectExecutor;
    private AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private AtomicBoolean reconnecting = new AtomicBoolean(false);

    // Reconnection configuration
    private static final int INITIAL_RECONNECT_DELAY_SECONDS = 5;
    private static final int MAX_RECONNECT_DELAY_SECONDS = 120;
    private static final int MAX_RECONNECT_ATTEMPTS = 10_000; // Effectively unlimited with exponential backoff

    /**
     * Initialize MQTT connection on startup if enabled
     */
    void onStart(@Observes StartupEvent ev) {
        if (!mqttConfig.isMqttEnabled()) {
            log.info("MQTT support is disabled - skipping MQTT client initialization");
            return;
        }

        // Initialize reconnection executor
        reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mqtt-reconnect-thread");
            t.setDaemon(true);
            return t;
        });

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
        // Shutdown reconnection executor
        if (reconnectExecutor != null) {
            reconnectExecutor.shutdownNow();
            log.info("MQTT reconnection executor shutdown");
        }

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

        // Using MemoryPersistence since we're running in containers
        // cleanSession=false ensures broker queues messages when we're disconnected
        mqttClient = new MqttClient(mqttConfig.getBrokerUrl(), CLIENT_ID, new MemoryPersistence());

        connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(false); // Keep session state on broker side
        connectOptions.setConnectionTimeout(30);
        connectOptions.setKeepAliveInterval(60);
        connectOptions.setAutomaticReconnect(false); // We handle reconnection manually

        // Additional resilience and stability settings
        connectOptions.setExecutorServiceTimeout(30);    // 30 second timeout for operations
        connectOptions.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1); // Force MQTT 3.1.1
        connectOptions.setServerURIs(new String[]{mqttConfig.getBrokerUrl()}); // Explicit server URI list
        connectOptions.setHttpsHostnameVerificationEnabled(false); // For internal networks
        connectOptions.setMaxInflight(1000); // Max unacknowledged messages

        if (mqttConfig.hasCredentials()) {
            connectOptions.setUserName(mqttConfig.getUsername());
            connectOptions.setPassword(mqttConfig.getPassword().toCharArray());
            log.debug("MQTT authentication configured for user: {}", mqttConfig.getUsername());
        }

        // Set callback for message handling with manual reconnection
        mqttClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect) {
                    log.info("MQTT reconnected successfully to: {}. Session state preserved with cleanSession=false", serverURI);
                    // Reset reconnect attempts counter on successful reconnection
                    reconnectAttempts.set(0);

                    // Re-subscribe after reconnection
                    try {
                        mqttClient.subscribe(TOPIC_PATTERN, 1);
                        log.info("MQTT re-subscribed to pattern: {} (QoS 1) after reconnection. Processing queued messages...", TOPIC_PATTERN);
                    } catch (MqttException e) {
                        log.error("Failed to re-subscribe after reconnection", e);
                    }
                } else {
                    log.info("MQTT initial connection completed successfully to: {}. Ready to receive messages.", serverURI);
                    reconnectAttempts.set(0);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.warn("MQTT connection lost. Manual reconnection will be attempted. " +
                        "Reason: {} - {}",
                        cause.getClass().getSimpleName(),
                        cause.getMessage());
                log.debug("MQTT connection lost - full stack trace:", cause);

                // Schedule manual reconnection with exponential backoff
                scheduleReconnect();
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                try {
                    handleMqttMessage(topic, new String(message.getPayload()));
                } catch (Exception e) {
                    // Log the error but don't let it propagate to MQTT client
                    // This ensures subsequent messages are still processed
                    log.error("Error processing MQTT message from topic: {} - {}", topic, new String(message.getPayload()), e);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Not used for incoming messages
            }
        });

        // Connect and subscribe
        mqttClient.connect(connectOptions);
        log.info("MQTT client connected successfully");

        mqttClient.subscribe(TOPIC_PATTERN, 1); // QoS 1 for at-least-once delivery
        log.info("MQTT subscription completed for pattern: {} with QoS 1", TOPIC_PATTERN);
    }

    /**
     * Schedule reconnection attempt with exponential backoff
     */
    private void scheduleReconnect() {
        // Prevent multiple overlapping reconnection attempts
        if (!reconnecting.compareAndSet(false, true)) {
            log.debug("Reconnection already in progress, skipping duplicate attempt");
            return;
        }

        int currentAttempt = reconnectAttempts.incrementAndGet();

        if (currentAttempt > MAX_RECONNECT_ATTEMPTS) {
            log.error("Max reconnection attempts ({}) reached. Stopping reconnection attempts.", MAX_RECONNECT_ATTEMPTS);
            reconnecting.set(false);
            return;
        }

        // Calculate delay with exponential backoff: initialDelay * 2^(attempt-1)
        // Capped at MAX_RECONNECT_DELAY_SECONDS
        int delaySeconds = Math.min(
                INITIAL_RECONNECT_DELAY_SECONDS * (1 << (currentAttempt - 1)),
                MAX_RECONNECT_DELAY_SECONDS
        );

        log.info("Scheduling MQTT reconnection attempt {} in {} seconds", currentAttempt, delaySeconds);

        reconnectExecutor.schedule(() -> {
            try {
                if (mqttClient.isConnected()) {
                    log.debug("MQTT client already connected, skipping reconnection attempt");
                    reconnectAttempts.set(0);
                    reconnecting.set(false);
                    return;
                }

                log.info("Attempting MQTT reconnection (attempt {})...", currentAttempt);
                mqttClient.connect(connectOptions);
                log.info("MQTT reconnection successful");

                // Re-subscribe after manual reconnection
                mqttClient.subscribe(TOPIC_PATTERN, 1);
                log.info("MQTT re-subscribed to pattern: {} (QoS 1). Processing queued messages...", TOPIC_PATTERN);

                // Reset attempts counter and reconnecting flag on success
                reconnectAttempts.set(0);
                reconnecting.set(false);

            } catch (MqttException e) {
                log.warn("MQTT reconnection attempt {} failed: {} - {}. Will retry...",
                        currentAttempt,
                        e.getClass().getSimpleName(),
                        e.getMessage());
                log.debug("Reconnection failure details:", e);

                // Reset reconnecting flag before scheduling next attempt
                reconnecting.set(false);

                // Schedule next attempt
                scheduleReconnect();
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    /**
     * Handle incoming MQTT messages
     */
    private void handleMqttMessage(String topic, String payload) {
        try {
            log.info("Received MQTT message on topic: {} - {}", topic, payload);

            // Parse topic: owntracks/{username}/{deviceId}
            String[] topicParts = topic.split("/");
            if (topicParts.length != 3 || !"owntracks".equals(topicParts[0])) {
                log.error("Invalid OwnTracks MQTT topic format: {}", topic);
                return;
            }

            String username = topicParts[1];
            String deviceId = topicParts[2];

            // Parse message payload
            Map<String, Object> messageData = OBJECT_MAPPER.readValue(payload, Map.class);

            // Skip non-location messages
            if (!"location".equals(messageData.get("_type"))) {
                log.error("Skipping non-location message: {}", messageData.get("_type"));
                return;
            }

            // Authenticate user
            Optional<GpsAuthenticationResult> userIdOpt = authRegistry.authenticateByUsername(username, GpsSourceType.OWNTRACKS);
            if (userIdOpt.isEmpty()) {
                log.error("Authentication failed for MQTT user: {}", username);
                return;
            }

            GpsAuthenticationResult authenticationResult = userIdOpt.get();
            OwnTracksLocationMessage locationMessage = OBJECT_MAPPER.convertValue(messageData, OwnTracksLocationMessage.class);

            if (timestampOverride) {
                if ("p".equals(locationMessage.getT())) {
                    locationMessage.setTst(Instant.now().getEpochSecond());
                }
            }

            // Handle POI if present
            if (locationMessage.getPoi() != null && !locationMessage.getPoi().trim().isEmpty()) {
                try {
                    ownTracksPoiService.handlePoi(locationMessage, authenticationResult.getUserId());
                } catch (Exception e) {
                    log.error("Failed to handle OwnTracks POI: {}", e.getMessage(), e);
                    // Continue processing GPS point even if POI handling fails
                }
            }

            // Handle tag (including null/empty to end active tags)
            try {
                ownTracksTagService.handleTag(locationMessage, authenticationResult.getUserId());
            } catch (Exception e) {
                log.error("Failed to handle OwnTracks tag: {}", e.getMessage(), e);
                // Continue processing GPS point even if tag handling fails
            }

            // Save GPS point
            gpsPointService.saveOwnTracksGpsPoint(locationMessage, authenticationResult.getUserId(), deviceId, GpsSourceType.OWNTRACKS, authenticationResult.getConfig());

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