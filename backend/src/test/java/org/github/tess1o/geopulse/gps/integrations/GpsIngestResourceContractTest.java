package org.github.tess1o.geopulse.gps.integrations;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.service.ApiTokenService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class GpsIngestResourceContractTest {

    private static final String PASSWORD = "password123";
    private static final String SOURCE_PASSWORD = "source-password";
    private static final String INGEST = "/api/v1/gps/ingest";

    @Inject
    UserService userService;

    @Inject
    ApiTokenService apiTokenService;

    @Inject
    GpsPointRepository gpsPointRepository;

    private UUID userId;
    private String apiToken;

    @BeforeEach
    @Transactional
    void setUp() {
        String email = TestIds.uniqueEmail("gps-ingest-contract");
        UserEntity user = userService.registerUser(email, PASSWORD, "GPS Ingest Contract User", "UTC");
        userId = user.getId();
        apiToken = apiTokenService.createToken(
                userId, "GPS ingest contract", Instant.now().plusSeconds(3600), "127.0.0.1"
        ).getToken();
    }

    @Test
    void allCanonicalIngestRoutesAcceptTheirNativePayloads() {
        String suffix = TestIds.uniqueValue("ingest");
        String ownTracksUser = "owntracks-" + suffix;
        String gpsLoggerUser = "gpslogger-" + suffix;
        String colotaUser = "colota-" + suffix;
        String overlandToken = "overland-" + suffix;
        String homeAssistantToken = "home-assistant-" + suffix;
        String traccarToken = "traccar-" + suffix;
        String dawarichToken = "dawarich-" + suffix;

        createBasicSource("OWNTRACKS", ownTracksUser);
        createBasicSource("GPSLOGGER", gpsLoggerUser);
        createBasicSource("COLOTA", colotaUser);
        createTokenSource("OVERLAND", overlandToken, null);
        createTokenSource("HOME_ASSISTANT", homeAssistantToken, null);
        createTokenSource("TRACCAR", traccarToken, "traccar-device");
        createTokenSource("DAWARICH", dawarichToken, null);

        given()
                .auth().preemptive().basic(ownTracksUser, SOURCE_PASSWORD)
                .header("X-Limit-D", "owntracks-device")
                .contentType(ContentType.JSON)
                .body(locationPayload(50.1, 30.1, 1780000001L))
                .when().post(INGEST + "/owntracks")
                .then().statusCode(200).body(equalTo("[]"));

        given()
                .auth().preemptive().basic(gpsLoggerUser, SOURCE_PASSWORD)
                .header("X-Limit-D", "gpslogger-device")
                .contentType(ContentType.JSON)
                .body(locationPayload(50.2, 30.2, 1780000002L))
                .when().post(INGEST + "/gpslogger")
                .then().statusCode(200).body(equalTo("[]"));

        given()
                .auth().preemptive().basic(colotaUser, SOURCE_PASSWORD)
                .contentType(ContentType.JSON)
                .body(Map.of("lat", 50.3, "lon", 30.3, "tst", 1780000003L, "acc", 5.0,
                        "alt", 103.0, "vel", 1.0, "batt", 83.0))
                .when().post(INGEST + "/colota")
                .then().statusCode(200).body(equalTo("[]"));

        given()
                .header("Authorization", "Bearer " + overlandToken)
                .contentType(ContentType.JSON)
                .body(Map.of("locations", List.of(Map.of(
                        "type", "Feature",
                        "geometry", Map.of("type", "Point", "coordinates", List.of(30.4, 50.4)),
                        "properties", Map.of(
                                "timestamp", "2026-05-27T20:53:24Z",
                                "device_id", "overland-device",
                                "horizontal_accuracy", 5.0,
                                "battery_level", 0.84,
                                "speed", 1.0,
                                "altitude", 104)
                ))))
                .when().post(INGEST + "/overland")
                .then().statusCode(200).body("result", equalTo("ok"));

        given()
                .header("Authorization", "Bearer " + homeAssistantToken)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "device_id", "home-assistant-device",
                        "timestamp", "2026-05-27T20:53:25Z",
                        "location", Map.of("latitude", 50.5, "longitude", 30.5, "accuracy", 5.0,
                                "altitude", 105.0, "speed", 1.0),
                        "battery", Map.of("level", 85)
                ))
                .when().post(INGEST + "/home-assistant")
                .then().statusCode(200);

        given()
                .header("Authorization", "Bearer " + traccarToken)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "device", Map.of("uniqueId", "traccar-device"),
                        "position", Map.of(
                                "latitude", 50.6,
                                "longitude", 30.6,
                                "fixTime", "2026-05-27T20:53:26Z",
                                "accuracy", 5.0,
                                "altitude", 106.0,
                                "speed", 1.0,
                                "attributes", Map.of("batteryLevel", 86)
                        )
                ))
                .when().post(INGEST + "/traccar")
                .then().statusCode(200);

        given()
                .header("Authorization", "Bearer " + dawarichToken)
                .contentType(ContentType.JSON)
                .body(Map.of("locations", List.of(Map.of(
                        "type", "Feature",
                        "geometry", Map.of("type", "Point", "coordinates", List.of(30.7, 50.7)),
                        "properties", Map.of(
                                "timestamp", "2026-05-27T20:53:27Z",
                                "device_id", "dawarich-device",
                                "vertical_accuracy", 5.0,
                                "altitude", 107.0,
                                "speed", 1.0)
                ))))
                .when().post(INGEST + "/dawarich/points")
                .then().statusCode(200);

        given()
                .header("Authorization", "Bearer " + dawarichToken)
                .when().get(INGEST + "/dawarich/health")
                .then()
                .statusCode(200)
                .header("X-Dawarich-Response", equalTo("Hey, I'm alive and authenticated!"))
                .header("X-Dawarich-Version", equalTo("0.30.8"))
                .body("status", equalTo("ok"));

        given()
                .queryParam("api_key", dawarichToken)
                .when().get(INGEST + "/dawarich/stats")
                .then()
                .statusCode(200)
                .body("totalDistanceKm", equalTo(1000))
                .body("yearlyStats[0].year", equalTo(2025));

        List<GpsPointEntity> points = gpsPointRepository.findByUserId(userId);
        assertThat(points).hasSize(7);
        assertThat(points).extracting(GpsPointEntity::getSourceType)
                .containsExactlyInAnyOrderElementsOf(EnumSet.of(
                        GpsSourceType.OWNTRACKS,
                        GpsSourceType.GPSLOGGER,
                        GpsSourceType.COLOTA,
                        GpsSourceType.OVERLAND,
                        GpsSourceType.HOME_ASSISTANT,
                        GpsSourceType.TRACCAR,
                        GpsSourceType.DAWARICH
                ));
        assertThat(points).allSatisfy(point -> assertThat(point.getCoordinates()).isNotNull());
    }

    @Test
    void nativeIntegrationCredentialsAreRequired() {
        assertProblemEnvelope(given()
                .contentType(ContentType.JSON)
                .body(locationPayload(50.1, 30.1, 1780000011L))
                .when().post(INGEST + "/owntracks"), 401, "AUTHENTICATION_REQUIRED");

        assertProblemEnvelope(given()
                .header("Authorization", "Bearer invalid-overland-token")
                .contentType(ContentType.JSON)
                .body(Map.of("locations", List.of()))
                .when().post(INGEST + "/overland"), 401, "AUTHENTICATION_REQUIRED");

        assertProblemEnvelope(given()
                .header("X-API-Key", apiToken)
                .contentType(ContentType.JSON)
                .body(Map.of("device", Map.of("uniqueId", "device")))
                .when().post(INGEST + "/traccar"), 401, "AUTHENTICATION_REQUIRED");

        assertProblemEnvelope(given()
                .when().get(INGEST + "/dawarich/stats"), 401, "AUTHENTICATION_REQUIRED");
    }

    private void createBasicSource(String type, String username) {
        Map<String, Object> source = source(type);
        source.put("username", username);
        source.put("password", SOURCE_PASSWORD);
        createSource(source);
    }

    private void createTokenSource(String type, String token, String deviceId) {
        Map<String, Object> source = source(type);
        source.put("token", token);
        if (deviceId != null) {
            source.put("deviceId", deviceId);
        }
        createSource(source);
    }

    private Map<String, Object> source(String type) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", type);
        source.put("connectionType", "HTTP");
        source.put("filterInaccurateData", false);
        source.put("enableDuplicateDetection", false);
        return source;
    }

    private void createSource(Map<String, Object> source) {
        given()
                .header("X-API-Key", apiToken)
                .contentType(ContentType.JSON)
                .body(source)
                .when().post("/api/v1/gps/sources/")
                .then()
                .statusCode(201)
                .body("type", equalTo(source.get("type")))
                .body("active", equalTo(true));
    }

    private Map<String, Object> locationPayload(double latitude, double longitude, long timestamp) {
        return Map.of(
                "_type", "location",
                "lat", latitude,
                "lon", longitude,
                "tst", timestamp,
                "acc", 5.0,
                "alt", 100.0,
                "vel", 1.0,
                "batt", 80.0
        );
    }
}
