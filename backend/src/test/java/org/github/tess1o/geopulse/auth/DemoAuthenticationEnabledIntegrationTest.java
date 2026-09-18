package org.github.tess1o.geopulse.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.model.DistanceUnit;
import org.github.tess1o.geopulse.user.model.TemperatureUnit;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@TestProfile(DemoAuthenticationEnabledIntegrationTest.DemoEnabledProfile.class)
@SerializedDatabaseTest
class DemoAuthenticationEnabledIntegrationTest {

    @Inject
    UserService userService;

    @Inject
    AuthenticationService authenticationService;

    private String demoAccessToken;

    @BeforeEach
    @Transactional
    void setUp() {
        ensureDemoUser(
                "new-york@demo.geopulse.cc",
                "New York Demo",
                "America/New_York",
                DistanceUnit.MILES,
                TemperatureUnit.FAHRENHEIT,
                "MDY",
                "12h"
        );
        ensureDemoUser(
                "paris@demo.geopulse.cc",
                "Paris Demo",
                "Europe/Paris",
                DistanceUnit.KILOMETERS,
                TemperatureUnit.CELSIUS,
                "DMY",
                "24h"
        );
        ensureDemoUser(
                "london@demo.geopulse.cc",
                "London Demo",
                "Europe/London",
                DistanceUnit.MILES,
                TemperatureUnit.CELSIUS,
                "DMY",
                "24h"
        );

        UserEntity demoUser = userService.findByEmail("new-york@demo.geopulse.cc").orElseThrow();
        demoAccessToken = authenticationService.createAuthResponse(demoUser).getAccessToken();
    }

    @Test
    void authStatus_WhenDemoModeEnabled_ExposesPublicPersonasOnly() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/v1/auth/sessions/current")
                .then()
                .statusCode(200)
                .body("demoModeEnabled", equalTo(true))
                .body("passwordRegistrationEnabled", equalTo(false))
                .body("oidcRegistrationEnabled", equalTo(false))
                .body("demoPersonas.id", contains("kyiv", "new-york", "london"))
                .body("demoPersonas[1].label", equalTo("🗽 Login as New York"))
                .body("demoPersonas[1].detail", equalTo("(Miles, 12-hour clock, US date format)"))
                .body("demoPersonas[1].email", nullValue());
    }

    @Test
    void demoLogin_WithConfiguredPersona_IssuesBrowserAuthCookies() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "personaId": "new-york"
                        }
                        """)
                .when()
                .post("/api/v1/auth/demo-sessions")
                .then()
                .statusCode(200)
                .body("user.email", equalTo("new-york@demo.geopulse.cc"))
                .body("user.demoMode", equalTo(true))
                .body("user.distanceUnit", equalTo("MILES"))
                .body("user.temperatureUnit", equalTo("FAHRENHEIT"))
                .body("user.dateFormat", equalTo("MDY"))
                .body("user.timeFormat", equalTo("12h"))
                .cookie("access_token", notNullValue())
                .cookie("refresh_token", notNullValue())
                .cookie("token_expires_at", notNullValue());
    }

    @Test
    void demoLogin_WithStartupProvisionedPersona_IssuesBrowserAuthCookies() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "personaId": "kyiv"
                        }
                        """)
                .when()
                .post("/api/v1/auth/demo-sessions")
                .then()
                .statusCode(200)
                .body("user.email", equalTo("kyiv@demo.geopulse.cc"))
                .body("user.demoMode", equalTo(true))
                .body("user.distanceUnit", equalTo("KILOMETERS"))
                .body("user.temperatureUnit", equalTo("CELSIUS"))
                .body("user.dateFormat", equalTo("DMY"))
                .body("user.timeFormat", equalTo("24h"))
                .cookie("access_token", notNullValue())
                .cookie("refresh_token", notNullValue())
                .cookie("token_expires_at", notNullValue());
    }

    @Test
    void demoLogin_WithUnknownPersona_ReturnsNotFoundWithoutCookies() {
        Response response = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "personaId": "unknown"
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
    void demoUser_WithReadOnlyAdminRole_CanReadAdminDashboard() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + demoAccessToken)
                .when()
                .get("/api/v1/admin/dashboard")
                .then()
                .statusCode(200)
                .body("totalUsers", notNullValue());
    }

    @Test
    void demoUser_WhenUpdatingAdminSettings_IsBlockedByDemoGuard() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + demoAccessToken)
                .body("""
                        {
                            "value": "false"
                        }
                        """)
                .when()
                .put("/api/v1/admin/settings/keys/auth.registration.enabled")
                .then()
                .statusCode(403);
    }

    @Test
    void demoUser_WhenUpdatingOwnProfile_IsBlockedByDemoGuard() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + demoAccessToken)
                .body("""
                        {
                            "fullName": "Changed Demo User",
                            "timezone": "Europe/Kyiv"
                        }
                        """)
                .when()
                .patch("/api/v1/users/me")
                .then()
                .statusCode(403)
                .header("X-GeoPulse-Demo-Blocked", "true");
    }

    @Test
    void demoMode_BlocksPublicRegistrationWrites() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "email": "blocked-public-registration@example.com",
                            "password": "DemoPassword123!",
                            "fullName": "Blocked Registration",
                            "timezone": "UTC"
                        }
                        """)
                .when()
                .post("/api/v1/registrations")
                .then()
                .statusCode(403)
                .header("X-GeoPulse-Demo-Blocked", "true");
    }

    @Test
    void demoMode_BlocksExportReads() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + demoAccessToken)
                .when()
                .get("/api/v1/exports")
                .then()
                .statusCode(403)
                .header("X-GeoPulse-Demo-Blocked", "true");
    }

    @Test
    void demoUser_WhenInitializingImportUpload_IsBlockedByDemoGuard() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + demoAccessToken)
                .body("""
                        {
                            "fileName": "demo.csv",
                            "fileSize": 128,
                            "importFormat": "csv",
                            "options": "{}"
                        }
                        """)
                .when()
                .post("/api/v1/import-uploads")
                .then()
                .statusCode(403)
                .header("X-GeoPulse-Demo-Blocked", "true");
    }

    private void ensureDemoUser(String email,
                                String fullName,
                                String timezone,
                                DistanceUnit distanceUnit,
                                TemperatureUnit temperatureUnit,
                                String dateFormat,
                                String timeFormat) {
        UserEntity user = userService.findByEmail(email)
                .orElseGet(() -> userService.registerUser(email, "DemoPassword123!", fullName, timezone));

        user.setFullName(fullName);
        user.setTimezone(timezone);
        user.setDistanceUnit(distanceUnit);
        user.setTemperatureUnit(temperatureUnit);
        user.setDateFormat(dateFormat);
        user.setTimeFormat(timeFormat);
        user.setActive(true);
        user.setRole(Role.USER);
    }

    public static class DemoEnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("geopulse.demo.enabled", "true");
        }
    }
}
