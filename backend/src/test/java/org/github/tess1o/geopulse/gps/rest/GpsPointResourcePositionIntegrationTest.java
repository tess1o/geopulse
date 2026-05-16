package org.github.tess1o.geopulse.gps.rest;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class GpsPointResourcePositionIntegrationTest {
    private static final String DEVICE_ID = "pixel-9-pro";


    @Inject
    UserService userService;

    @Inject
    AuthenticationService authenticationService;

    @Inject
    GpsPointRepository gpsPointRepository;

    private UUID userId;
    private String accessToken;

    @BeforeEach
    @Transactional
    void setUp() {
        UserEntity user = userService.registerUser(
                "gps-position-" + System.nanoTime() + "@example.com",
                "password123",
                "GPS Position User",
                "UTC"
        );
        userId = user.getId();

        AuthResponse auth = authenticationService.authenticate(user.getEmail(), "password123");
        accessToken = auth.getAccessToken();
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
                .post("/api/gps/points")
                .then()
                .statusCode(200)
                .body("status", equalTo("success"))
                .body("message", equalTo("OK"));

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
                .post("/api/gps/points")
                .then()
                .statusCode(200)
                .body("status", equalTo("success"))
                .body("message", equalTo("OK"));

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
                .post("/api/gps/points")
                .then()
                .statusCode(200)
                .body("status", equalTo("success"))
                .body("message", equalTo("OK"));

        List<GpsPointEntity> savedPoints = gpsPointRepository.findByUserId(userId);
        assertThat(savedPoints).isEmpty();
    }
}
