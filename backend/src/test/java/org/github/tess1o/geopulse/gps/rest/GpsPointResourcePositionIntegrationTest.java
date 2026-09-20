package org.github.tess1o.geopulse.gps.rest;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.testsupport.AsyncTimelineTestMocks;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class GpsPointResourcePositionIntegrationTest {
    private static final String DEVICE_ID = "pixel-9-pro";
    private static final String GPS_POINTS = "/api/v1/gps/points";

    @Inject
    UserService userService;

    @Inject
    AuthenticationService authenticationService;

    @Inject
    GpsPointRepository gpsPointRepository;

    private UUID userId;
    private UUID otherUserId;
    private String accessToken;
    private String otherAccessToken;

    @BeforeEach
    @Transactional
    void setUp() {
        GeocodingTestMocks.install();
        AsyncTimelineTestMocks.install();
        UserEntity user = userService.registerUser(
                TestIds.uniqueEmail("gps-position"),
                "password123",
                "GPS Position User",
                "UTC"
        );
        userId = user.getId();

        AuthResponse auth = authenticationService.authenticate(user.getEmail(), "password123");
        accessToken = auth.getAccessToken();

        UserEntity other = userService.registerUser(
                TestIds.uniqueEmail("gps-position-other"),
                "password123",
                "Other GPS Position User",
                "UTC"
        );
        otherUserId = other.getId();
        otherAccessToken = authenticationService.authenticate(other.getEmail(), "password123").getAccessToken();
    }

    @Test
    void shouldRequireAuthenticationForGpsStatus() {
        given()
                .when()
                .get("/api/v1/gps/points/status")
                .then()
                .statusCode(401);
    }

    @Test
    void shouldReturnGpsStatusForAuthenticatedUser() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .get("/api/v1/gps/points/status")
                .then()
                .statusCode(200)
                .body("hasGpsData", equalTo(false))
                .body("totalGpsPoints", equalTo(0))
                .body("latestGpsPointTimestamp", nullValue())
                .body("coordinates", nullValue());
    }

    @Test
    void shouldAcceptRealSaveMobileLocationJsonAndPersistPoint() {
        String payload = """
                {
                  "points": [
                    {
                      "id": 0,
                      "timestamp": "2026-05-15T10:00:00Z",
                      "coordinates": {
                        "lat": 59.3293,
                        "lng": 18.0686
                      },
                      "accuracy": 5.0,
                      "battery": 89.0,
                      "velocity": 1.5,
                      "altitude": 20.0
                    }
                  ]
                }
                """;

        given()
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Device-Id", DEVICE_ID)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/gps/points")
                .then()
                .statusCode(200)
                .body("status", equalTo("success"))
                .body("data", equalTo("OK"));

        List<GpsPointEntity> savedPoints = gpsPointRepository.findByUserId(userId);
        assertThat(savedPoints).hasSize(1);

        GpsPointEntity savedPoint = savedPoints.getFirst();
        assertThat(savedPoint.getSourceType()).isEqualTo(GpsSourceType.MOBILE_APP);
        assertThat(savedPoint.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(savedPoint.getTimestamp()).hasToString("2026-05-15T10:00:00Z");
        assertThat(savedPoint.getCoordinates().getY()).isEqualTo(59.3293);
        assertThat(savedPoint.getCoordinates().getX()).isEqualTo(18.0686);
        assertThat(savedPoint.getAccuracy()).isEqualTo(5.0);
        assertThat(savedPoint.getBattery()).isEqualTo(89.0);
        assertThat(savedPoint.getAltitude()).isEqualTo(20.0);
        assertThat(savedPoint.getVelocity()).isEqualTo(5.4);
    }

    @Test
    void shouldAcceptPayloadWithoutIdAndPersistPoint() {
        String payload = """
                {
                  "points": [
                    {
                      "timestamp": "2026-05-15T11:00:00Z",
                      "coordinates": {
                        "lat": 59.3293,
                        "lng": 18.0686
                      },
                      "accuracy": 4.0,
                      "battery": 88.0,
                      "velocity": 2.0,
                      "altitude": 21.0
                    }
                  ]
                }
                """;

        given()
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Device-Id", DEVICE_ID)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/gps/points")
                .then()
                .log().body(true)
                .statusCode(200)
                .body("status", equalTo("success"))
                .body("data", equalTo("OK"));

        List<GpsPointEntity> savedPoints = gpsPointRepository.findByUserId(userId);
        assertThat(savedPoints).hasSize(1);

        GpsPointEntity savedPoint = savedPoints.getFirst();
        assertThat(savedPoint.getId()).isNotNull();
        assertThat(savedPoint.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(savedPoint.getTimestamp()).hasToString("2026-05-15T11:00:00Z");
        assertThat(savedPoint.getCoordinates().getY()).isEqualTo(59.3293);
        assertThat(savedPoint.getCoordinates().getX()).isEqualTo(18.0686);
    }

    @Test
    void shouldIgnorePayloadWithoutTimestamp() {
        String payload = """
                {
                  "points": [
                    {
                      "id": 0,
                      "coordinates": {
                        "lat": 59.3293,
                        "lng": 18.0686
                      },
                      "accuracy": 5.0,
                      "battery": 89.0,
                      "velocity": 1.5,
                      "altitude": 20.0
                    }
                  ]
                }
                """;

        given()
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Device-Id", DEVICE_ID)
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/api/v1/gps/points")
                .then()
                .statusCode(200)
                .body("status", equalTo("success"))
                .body("data", equalTo("OK"));

        List<GpsPointEntity> savedPoints = gpsPointRepository.findByUserId(userId);
        assertThat(savedPoints).isEmpty();
    }

    @Test
    void shouldServeEveryGpsReadRoute() {
        Instant timestamp = Instant.parse("2026-05-15T12:00:00Z");
        long pointId = ingestPoint(accessToken, userId, timestamp, 59.3293, 18.0686);

        Response path = authenticated(accessToken)
                .queryParam("from", "2026-05-15T11:00:00Z")
                .queryParam("to", "2026-05-15T13:00:00Z")
                .queryParam("simplify", false)
                .when().get(GPS_POINTS + "/path");
        assertThat(path.statusCode()).isEqualTo(200);
        assertThat(path.jsonPath().getInt("pointCount")).isEqualTo(1);

        Response map = authenticated(accessToken)
                .queryParam("from", "2026-05-15T11:00:00Z")
                .queryParam("to", "2026-05-15T13:00:00Z")
                .when().get(GPS_POINTS + "/map");
        assertThat(map.statusCode()).isEqualTo(200);
        assertThat(map.jsonPath().getInt("returnedCount")).isEqualTo(1);

        Response location = authenticated(accessToken).when().get(GPS_POINTS + "/" + pointId + "/location");
        assertThat(location.statusCode()).isEqualTo(200);
        assertThat(location.jsonPath().getString("locationName")).isEqualTo("Kyiv, Ukraine");

        Response summary = authenticated(accessToken).when().get(GPS_POINTS + "/summary");
        assertThat(summary.statusCode()).isEqualTo(200);
        assertThat(summary.jsonPath().getLong("totalPoints")).isEqualTo(1);

        Response page = authenticated(accessToken).when().get(GPS_POINTS);
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.jsonPath().getLong("totalElements")).isEqualTo(1);
        assertThat(page.jsonPath().getList("items.id", Long.class)).contains(pointId);

        Response export = authenticated(accessToken).when().get(GPS_POINTS + "/exports");
        assertThat(export.statusCode()).isEqualTo(200);
        assertThat(export.contentType()).startsWith("text/csv");
        assertThat(export.body().asString()).contains(timestamp.toString());

        Response latest = authenticated(accessToken).when().get(GPS_POINTS + "/latest");
        assertThat(latest.statusCode()).isEqualTo(200);
        assertThat(latest.jsonPath().getLong("id")).isEqualTo(pointId);
    }

    @Test
    void shouldUpdateDeleteOneBulkDeleteAndDeleteAll() {
        long updatedId = ingestPoint(accessToken, userId, Instant.parse("2026-05-15T13:00:00Z"), 50.0, 30.0);
        Response updated = authenticated(accessToken)
                .body(Map.of(
                        "coordinates", Map.of("lat", 50.5, "lng", 30.5),
                        "velocity", 12.0,
                        "accuracy", 3.0))
                .when().put(GPS_POINTS + "/" + updatedId);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(updated.jsonPath().getDouble("coordinates.lat")).isEqualTo(50.5);
        assertThat(updated.jsonPath().getDouble("velocity")).isEqualTo(12.0);

        Response deleted = authenticated(accessToken).when().delete(GPS_POINTS + "/" + updatedId);
        assertThat(deleted.statusCode()).isEqualTo(200);
        assertThat(deleted.jsonPath().getInt("deletedCount")).isEqualTo(1);

        long firstBulkId = ingestPoint(accessToken, userId, Instant.parse("2026-05-15T14:00:00Z"), 51.0, 31.0);
        long secondBulkId = ingestPoint(accessToken, userId, Instant.parse("2026-05-15T14:01:00Z"), 51.1, 31.1);
        Response bulkDeleted = authenticated(accessToken)
                .body(Map.of("gpsPointIds", List.of(firstBulkId, secondBulkId)))
                .when().post(GPS_POINTS + "/bulk");
        assertThat(bulkDeleted.statusCode()).isEqualTo(200);
        assertThat(bulkDeleted.jsonPath().getInt("deletedCount")).isEqualTo(2);

        ingestPoint(accessToken, userId, Instant.parse("2026-05-15T15:00:00Z"), 52.0, 32.0);
        Response deletedAll = authenticated(accessToken).when().delete(GPS_POINTS);
        assertThat(deletedAll.statusCode()).isEqualTo(204);
        assertThat(authenticated(accessToken).when().get(GPS_POINTS).jsonPath().getLong("totalElements")).isZero();
    }

    @Test
    void shouldUseExactGpsErrorContracts() {
        long otherPointId = ingestPoint(
                otherAccessToken, otherUserId, Instant.parse("2026-05-15T16:00:00Z"), 53.0, 33.0);

        assertProblemEnvelope(authenticated(accessToken).when().get(GPS_POINTS + "/" + otherPointId + "/location"),
                403, "GPS_POINT_ACCESS_DENIED");
        assertProblemEnvelope(authenticated(accessToken)
                        .body(Map.of("coordinates", Map.of("lat", 50.0, "lng", 30.0)))
                        .when().put(GPS_POINTS + "/" + otherPointId),
                403, "GPS_POINT_ACCESS_DENIED");
        assertProblemEnvelope(authenticated(accessToken).when().delete(GPS_POINTS + "/" + otherPointId),
                403, "GPS_POINT_ACCESS_DENIED");
        assertProblemEnvelope(authenticated(accessToken).when().get(GPS_POINTS + "/999999999/location"),
                404, "GPS_POINT_NOT_FOUND");
        assertProblemEnvelope(authenticated(accessToken).queryParam("from", "not-an-instant")
                        .when().get(GPS_POINTS + "/path"),
                400, "INVALID_GPS_QUERY");
    }

    @Test
    void duplicateMobilePointIsAcceptedOnceInsteadOfReturningConflict() {
        Instant timestamp = Instant.parse("2026-05-15T17:00:00Z");
        ingestPoint(accessToken, userId, timestamp, 54.0, 34.0);

        Response duplicate = authenticated(accessToken)
                .header("X-Device-Id", DEVICE_ID)
                .body(pointRequest(timestamp, 54.0, 34.0))
                .when().post(GPS_POINTS);

        assertThat(duplicate.statusCode()).isEqualTo(200);
        assertThat(gpsPointRepository.findByUserId(userId)).hasSize(1);
    }

    private long ingestPoint(String token, UUID id, Instant timestamp, double latitude, double longitude) {
        authenticated(token)
                .header("X-Device-Id", DEVICE_ID)
                .body(pointRequest(timestamp, latitude, longitude))
                .when().post(GPS_POINTS)
                .then().statusCode(200);
        return gpsPointRepository.findByUserId(id).stream()
                .filter(point -> timestamp.equals(point.getTimestamp()))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private static Map<String, Object> pointRequest(Instant timestamp, double latitude, double longitude) {
        return Map.of("points", List.of(Map.of(
                "timestamp", timestamp.toString(),
                "coordinates", Map.of("lat", latitude, "lng", longitude),
                "accuracy", 5.0,
                "battery", 89.0,
                "velocity", 1.5,
                "altitude", 20.0)));
    }

    private static RequestSpecification authenticated(String token) {
        return given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token);
    }
}
