package org.github.tess1o.geopulse.gps.integrations;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class GpsIntegrationRouteTest {

    @Test
    void ownTracksEndpointIsRegistered() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "_type", "location",
                        "lat", 42.7,
                        "lon", 23.3,
                        "tst", 1715770000L
                ))
                .when()
                .post("/api/owntracks")
                .then()
                .statusCode(401);
    }

    @Test
    void colotaEndpointIsRegistered() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "lat", 42.7,
                        "lon", 23.3,
                        "tst", 1715770000L
                ))
                .when()
                .post("/api/colota")
                .then()
                .statusCode(401);
    }

    @Test
    void gpsLoggerEndpointIsRegistered() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "_type", "location",
                        "lat", 42.7,
                        "lon", 23.3,
                        "tst", 1715770000L
                ))
                .when()
                .post("/api/gpslogger")
                .then()
                .statusCode(401);
    }

    @Test
    void traccarEndpointIsRegistered() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of())
                .when()
                .post("/api/traccar")
                .then()
                .statusCode(401);
    }

    @Test
    void homeAssistantEndpointIsRegistered() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of())
                .when()
                .post("/api/homeassistant")
                .then()
                .statusCode(401);
    }

    @Test
    void overlandEndpointIsRegistered() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("locations", List.of()))
                .when()
                .post("/api/overland")
                .then()
                .statusCode(401);
    }
}
