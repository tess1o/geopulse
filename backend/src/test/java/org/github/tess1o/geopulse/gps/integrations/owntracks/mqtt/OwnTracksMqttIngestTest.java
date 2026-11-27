package org.github.tess1o.geopulse.gps.integrations.owntracks.mqtt;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.github.tess1o.geopulse.auth.service.ApiTokenService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.testsupport.MosquittoTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * OwnTracks over MQTT, end to end on the JVM: a real Mosquitto broker, the backend's subscriber and the database.
 * This covers connecting, subscribing, authenticating by topic and saving points. It cannot catch native-image
 * registration problems (the JVM's ServiceLoader always finds Paho's own descriptor); the e2e spec
 * {@code location-sources.spec.js} runs the same flow against the native image.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@QuarkusTestResource(value = MosquittoTestResource.class, restrictToAnnotatedClass = true)
@SerializedDatabaseTest
class OwnTracksMqttIngestTest {

    private static final String SOURCES = "/api/v1/gps/sources";
    private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(15);

    @Inject
    UserService userService;

    @Inject
    ApiTokenService apiTokenService;

    @Inject
    MqttConfiguration mqttConfiguration;

    @Inject
    OwnTracksMqttService mqttService;

    @Inject
    GpsPointRepository gpsPointRepository;

    private UUID userId;
    private String apiToken;

    @BeforeEach
    @Transactional
    void setUp() {
        UserEntity user = userService.registerUser(TestIds.uniqueEmail("owntracks-mqtt"), "password123",
                "OwnTracks MQTT User", "UTC");
        userId = user.getId();
        apiToken = apiTokenService.createToken(
                user.getId(), "OwnTracks MQTT", Instant.now().plusSeconds(3600), "127.0.0.1"
        ).getToken();
    }

    @Test
    void connectsToTheBrokerAndSavesALocationPublishedOnTheUsersTopic() throws Exception {
        assertThat(mqttService.isActive()).as("backend MQTT client connected and subscribed").isTrue();
        String username = createMqttSource();

        publish("owntracks/" + username + "/phone", location(50.4501, 30.5234, 1780000100L));

        GpsPointEntity point = awaitNonNull(() -> latestPoint(userId));
        assertThat(point.getLatitude()).isEqualTo(50.4501);
        assertThat(point.getLongitude()).isEqualTo(30.5234);
        assertThat(point.getTimestamp()).isEqualTo(Instant.ofEpochSecond(1780000100L));
        assertThat(point.getDeviceId()).isEqualTo("phone");
        assertThat(point.getSourceType()).isEqualTo(GpsSourceType.OWNTRACKS);
    }

    @Test
    void skipsMessagesItCannotUseAndKeepsProcessing() throws Exception {
        String username = createMqttSource();

        publish("owntracks/" + username + "/phone", "{\"_type\":\"transition\",\"tst\":1780000200}");
        publish("owntracks/unknown-" + TestIds.uniqueValue("mqtt") + "/phone", location(1.0, 1.0, 1780000250L));
        publish("owntracks/" + username, location(2.0, 2.0, 1780000260L));
        publish("owntracks/" + username + "/phone", "not json");
        publish("owntracks/" + username + "/phone", location(50.4502, 30.5235, 1780000300L));

        GpsPointEntity point = awaitNonNull(() -> latestPoint(userId));
        assertThat(point.getTimestamp()).isEqualTo(Instant.ofEpochSecond(1780000300L));
        assertThat(pointCount(userId)).isEqualTo(1);
    }

    private String createMqttSource() {
        String username = TestIds.uniqueValue("mqtt-user");
        given()
                .header("X-API-Key", apiToken)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "type", "OWNTRACKS",
                        "connectionType", "MQTT",
                        "username", username,
                        "password", "mqtt-password",
                        "filterInaccurateData", false,
                        "enableDuplicateDetection", false))
                .when().post(SOURCES + "/")
                .then().statusCode(201);
        return username;
    }

    private void publish(String topic, String payload) throws Exception {
        MqttClient publisher = new MqttClient(mqttConfiguration.getBrokerUrl(),
                "test-publisher-" + UUID.randomUUID(), new MemoryPersistence());
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            publisher.connect(options);
            publisher.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 1, false);
            publisher.disconnect();
        } finally {
            publisher.close();
        }
    }

    private static String location(double lat, double lon, long tst) {
        return "{\"_type\":\"location\",\"lat\":" + lat + ",\"lon\":" + lon + ",\"tst\":" + tst + ",\"acc\":5}";
    }

    private GpsPointEntity latestPoint(UUID user) {
        return QuarkusTransaction.requiringNew().call(() -> gpsPointRepository.findByUserIdLatestGpsPoint(user));
    }

    private long pointCount(UUID user) {
        return QuarkusTransaction.requiringNew().call(() -> gpsPointRepository.count("user.id", user));
    }

    private static <T> T awaitNonNull(Supplier<T> supplier) throws InterruptedException {
        Instant deadline = Instant.now().plus(DELIVERY_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            T value = supplier.get();
            if (value != null) {
                return value;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Nothing arrived over MQTT within " + DELIVERY_TIMEOUT);
    }
}
