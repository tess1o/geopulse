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
                .get("/api/auth/status")
                .then()
                .statusCode(200)
                .body("status", equalTo("success"))
                .body("data.demoModeEnabled", equalTo(false))
                .body("data.demoPersonas", empty());
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
                .post("/api/auth/demo-login")
                .then()
                .statusCode(404)
                .body("status", equalTo("error"))
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
                .post("/api/users/register")
                .then()
                .statusCode(201)
                .body("status", equalTo("success"))
                .body("data.email", equalTo(email))
                .body("data.demoMode", equalTo(false));
    }
}
