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
import org.github.tess1o.geopulse.user.model.MeasureUnit;
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
                MeasureUnit.IMPERIAL,
                "MDY",
                "12h"
        );
        ensureDemoUser(
                "paris@demo.geopulse.cc",
                "Paris Demo",
                "Europe/Paris",
                MeasureUnit.METRIC,
                "DMY",
                "24h"
        );
        ensureDemoUser(
                "london@demo.geopulse.cc",
                "London Demo",
                "Europe/London",
                MeasureUnit.IMPERIAL,
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
                .get("/api/auth/status")
                .then()
                .statusCode(200)
                .body("status", equalTo("success"))
                .body("data.demoModeEnabled", equalTo(true))
                .body("data.passwordRegistrationEnabled", equalTo(false))
                .body("data.oidcRegistrationEnabled", equalTo(false))
                .body("data.demoPersonas.id", contains("kyiv", "new-york", "london"))
                .body("data.demoPersonas[1].label", equalTo("🗽 Login as New York"))
                .body("data.demoPersonas[1].detail", equalTo("(Miles, 12-hour clock, US date format)"))
                .body("data.demoPersonas[1].email", nullValue());
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
                .post("/api/auth/demo-login")
                .then()
                .statusCode(200)
                .body("status", equalTo("success"))
                .body("data.user.email", equalTo("new-york@demo.geopulse.cc"))
                .body("data.user.demoMode", equalTo(true))
                .body("data.user.measureUnit", equalTo("IMPERIAL"))
                .body("data.user.dateFormat", equalTo("MDY"))
                .body("data.user.timeFormat", equalTo("12h"))
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
                .post("/api/auth/demo-login")
                .then()
                .statusCode(404)
                .body("status", equalTo("error"))
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
                .get("/api/admin/dashboard/stats")
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
                .put("/api/admin/settings/auth.registration.enabled")
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
                .post("/api/users/update")
                .then()
                .statusCode(403)
                .header("X-GeoPulse-Demo-Blocked", "true")
                .body("status", equalTo("error"));
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
                .post("/api/users/register")
                .then()
                .statusCode(403)
                .header("X-GeoPulse-Demo-Blocked", "true")
                .body("status", equalTo("error"));
    }

    @Test
    void demoMode_BlocksExportReads() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + demoAccessToken)
                .when()
                .get("/api/export/jobs")
                .then()
                .statusCode(403)
                .header("X-GeoPulse-Demo-Blocked", "true")
                .body("status", equalTo("error"));
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
                .post("/api/import/upload/init")
                .then()
                .statusCode(403)
                .header("X-GeoPulse-Demo-Blocked", "true")
                .body("status", equalTo("error"));
    }

    private void ensureDemoUser(String email,
                                String fullName,
                                String timezone,
                                MeasureUnit measureUnit,
                                String dateFormat,
                                String timeFormat) {
        UserEntity user = userService.findByEmail(email)
                .orElseGet(() -> userService.registerUser(email, "DemoPassword123!", fullName, timezone));

        user.setFullName(fullName);
        user.setTimezone(timezone);
        user.setMeasureUnit(measureUnit);
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
