package org.github.tess1o.geopulse.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
class DemoAuthenticationDisabledIntegrationTest {

    @Test
    void authStatus_WhenDemoModeDisabled_DoesNotExposePersonas() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/v1/auth/sessions/current")
                .then()
                .statusCode(200)
                .body("demoModeEnabled", equalTo(false))
                .body("demoPersonas", empty());
    }

    @Test
    void demoLogin_WhenDemoModeDisabled_ReturnsNotFoundWithoutCookies() {
        Response response = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "personaId": "new-york"
                        }
                        """)
                .when()
                .post("/api/v1/auth/demo-sessions")
                .then()
                .statusCode(404)
                .extract()
                .response();

        assertTrue(response.getDetailedCookies().asList().isEmpty());
    }

    @Test
    void publicRegistration_WhenDemoModeDisabled_StillSucceeds() {
        String email = TestIds.uniqueEmail("demo-disabled-register");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "email": "%s",
                            "password": "DemoPassword123!",
                            "fullName": "Normal Registration",
                            "timezone": "UTC"
                        }
                        """.formatted(email))
                .when()
                .post("/api/v1/registrations")
                .then()
                .statusCode(201)
                .body("email", equalTo(email))
                .body("demoMode", equalTo(false));
    }
}
