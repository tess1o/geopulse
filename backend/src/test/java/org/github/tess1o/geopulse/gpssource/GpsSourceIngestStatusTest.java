package org.github.tess1o.geopulse.gpssource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.service.ApiTokenService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceIngestStatusRepository;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class GpsSourceIngestStatusTest {

    private static final String SOURCES = "/api/v1/gps/sources";
    private static final String INGEST = "/api/v1/gps/ingest";
    private static final String SOURCE_PASSWORD = "source-password";

    @Inject
    UserService userService;

    @Inject
    ApiTokenService apiTokenService;

    @Inject
    GpsSourceIngestStatusRepository statusRepository;

    private String apiToken;

    @BeforeEach
    @Transactional
    void setUp() {
        UserEntity user = userService.registerUser(TestIds.uniqueEmail("gps-ingest-status"), "password123",
                "GPS Ingest Status User", "UTC");
        apiToken = apiTokenService.createToken(
                user.getId(), "GPS ingest status", Instant.now().plusSeconds(3600), "127.0.0.1"
        ).getToken();
    }

    @Test
    void statusIsEmptyUntilTheSourceReceivesData() {
        String sourceId = createSource(tokenSource("HOME_ASSISTANT", "ha-status-empty-" + TestIds.uniqueValue("ha")));

        assertThat(sourceStatus(sourceId)).isNull();
    }

    @Test
    void statusTracksSavedPointsAndTheLatestSkippedPoint() {
        String token = "ha-status-" + TestIds.uniqueValue("ha");
        Map<String, Object> source = tokenSource("HOME_ASSISTANT", token);
        source.put("filterInaccurateData", true);
        source.put("maxAllowedAccuracy", 50);
        source.put("maxAllowedSpeed", 250);
        String sourceId = createSource(source);

        sendHomeAssistantPoint(token, "2026-05-28T08:00:00Z", 10.0);
        sendHomeAssistantPoint(token, "2026-05-28T08:05:00Z", 120.0);

        Map<String, Object> status = sourceStatus(sourceId);
        assertThat(status).isNotNull();
        assertThat(status.get("lastReceivedAt")).isNotNull();
        assertThat(status.get("lastSavedAt")).isNotNull();
        assertThat(Instant.parse((String) status.get("lastSavedPointTimestamp")))
                .isEqualTo(Instant.parse("2026-05-28T08:00:00Z"));
        assertThat(status.get("lastRejectedAt")).isNotNull();
        assertThat(status.get("lastRejectionCode")).isEqualTo("ACCURACY_EXCEEDED");
        assertThat(status.get("lastRejectionParams"))
                .isEqualTo(Map.of("accuracy", 120.0f, "threshold", 50));
        assertThat(status.get("lastAuthFailureAt")).isNull();
    }

    @Test
    void wrongPasswordIsRecordedForTheIdentifiedSource() {
        String username = "owntracks-status-" + TestIds.uniqueValue("ot");
        Map<String, Object> source = baseSource("OWNTRACKS");
        source.put("username", username);
        source.put("password", SOURCE_PASSWORD);
        String sourceId = createSource(source);

        given()
                .auth().preemptive().basic(username, "wrong-password")
                .contentType(ContentType.JSON)
                .body(ownTracksLocation())
                .when().post(INGEST + "/owntracks")
                .then().statusCode(401);

        Map<String, Object> status = sourceStatus(sourceId);
        assertThat(status.get("lastAuthFailureCode")).isEqualTo("INVALID_CREDENTIALS");
        assertThat(status.get("lastAuthFailureAt")).isNotNull();
        assertThat(status.get("lastReceivedAt")).isNull();

        given()
                .auth().preemptive().basic(username, SOURCE_PASSWORD)
                .contentType(ContentType.JSON)
                .body(ownTracksLocation())
                .when().post(INGEST + "/owntracks")
                .then().statusCode(200);

        Map<String, Object> recovered = sourceStatus(sourceId);
        assertThat(recovered.get("lastReceivedAt")).isNotNull();
        assertThat(recovered.get("lastAuthFailureCode")).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void statusIsDeletedWithItsSource() {
        String token = "ha-status-delete-" + TestIds.uniqueValue("ha");
        String sourceId = createSource(tokenSource("HOME_ASSISTANT", token));
        sendHomeAssistantPoint(token, "2026-05-28T09:00:00Z", 5.0);
        assertThat(statusRepository.count("sourceConfigId", UUID.fromString(sourceId))).isEqualTo(1);

        given()
                .header("X-API-Key", apiToken)
                .when().delete(SOURCES + "/" + sourceId)
                .then().statusCode(204);

        assertThat(statusRepository.count("sourceConfigId", UUID.fromString(sourceId))).isZero();
    }

    private void sendHomeAssistantPoint(String token, String timestamp, double accuracy) {
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "device_id", "ha-status-device",
                        "timestamp", timestamp,
                        "location", Map.of("latitude", 50.4501, "longitude", 30.5234, "accuracy", accuracy)
                ))
                .when().post(INGEST + "/home-assistant")
                .then().statusCode(200);
    }

    private Map<String, Object> sourceStatus(String sourceId) {
        JsonPath sources = given()
                .header("X-API-Key", apiToken)
                .when().get(SOURCES)
                .then().statusCode(200)
                .extract().jsonPath();
        return sources.getMap("find { it.id == '" + sourceId + "' }.ingestStatus");
    }

    private String createSource(Map<String, Object> source) {
        return given()
                .header("X-API-Key", apiToken)
                .contentType(ContentType.JSON)
                .body(source)
                .when().post(SOURCES + "/")
                .then().statusCode(201)
                .extract().path("id");
    }

    private Map<String, Object> tokenSource(String type, String token) {
        Map<String, Object> source = baseSource(type);
        source.put("token", token);
        return source;
    }

    private Map<String, Object> baseSource(String type) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", type);
        source.put("connectionType", "HTTP");
        source.put("filterInaccurateData", false);
        source.put("enableDuplicateDetection", false);
        return source;
    }

    private Map<String, Object> ownTracksLocation() {
        return Map.of("_type", "location", "lat", 50.45, "lon", 30.52, "tst", 1780000100L, "acc", 5.0);
    }
}
