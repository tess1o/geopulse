package org.github.tess1o.geopulse.auth.session;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.admin.service.AdminUserService;
import org.github.tess1o.geopulse.auth.session.model.SessionRevocationReason;
import org.github.tess1o.geopulse.auth.session.model.UserSessionEntity;
import org.github.tess1o.geopulse.auth.session.repository.UserSessionRepository;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestActor;
import org.github.tess1o.geopulse.testsupport.TestUserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class UserSessionIntegrationTest {

    private static final String SESSION_COOKIE = "gp_session";

    @Inject
    TestUserFactory testUserFactory;

    @Inject
    UserSessionRepository sessionRepository;

    @Inject
    AdminUserService adminUserService;

    private TestActor actor;

    @BeforeEach
    void setUp() {
        actor = QuarkusTransaction.requiringNew().call(() -> testUserFactory.createUser("session-user"));
    }

    @Test
    void browserSignInIssuesOpaqueSessionCookieThatLogoutEndsImmediately() {
        String session = browserSignIn();
        assertThat(session).startsWith("gp_bs_");
        assertThat(session.split("\\.")).hasSize(1); // not a JWT

        given().cookie(SESSION_COOKIE, session).when().get("/api/v1/users/me").then().statusCode(200);

        Response logout = given().cookie(SESSION_COOKIE, session).when().delete("/api/v1/auth/sessions/current");
        assertThat(logout.statusCode()).isEqualTo(200);
        assertThat(logout.jsonPath().getString("idpLogoutUrl")).isNull();
        assertThat(logout.getDetailedCookie(SESSION_COOKIE).getMaxAge()).isZero();

        Response afterLogout = given().cookie(SESSION_COOKIE, session).when().get("/api/v1/users/me");
        assertThat(afterLogout.statusCode()).isEqualTo(401);
        assertThat(afterLogout.jsonPath().getString("code")).isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    void signingInAgainRevokesTheSessionTheBrowserAlreadyCarried() {
        String first = browserSignIn();
        String second = given()
                .contentType(ContentType.JSON)
                .cookie(SESSION_COOKIE, first)
                .body(Map.of("email", actor.email(), "password", actor.password()))
                .when().post("/api/v1/auth/sessions")
                .then().statusCode(200)
                .extract().cookie(SESSION_COOKIE);

        given().cookie(SESSION_COOKIE, first).when().get("/api/v1/users/me").then().statusCode(401);
        given().cookie(SESSION_COOKIE, second).when().get("/api/v1/users/me").then().statusCode(200);
    }

    @Test
    void apiSessionRefreshRotatesBothTokens() {
        Response login = apiSignIn();
        String access = login.jsonPath().getString("accessToken");
        String refresh = login.jsonPath().getString("refreshToken");
        assertThat(access).startsWith("gp_at_");
        assertThat(refresh).startsWith("gp_rt_");

        Response refreshed = refresh(refresh);
        assertThat(refreshed.statusCode()).isEqualTo(200);
        String newAccess = refreshed.jsonPath().getString("accessToken");
        String newRefresh = refreshed.jsonPath().getString("refreshToken");
        assertThat(newAccess).isNotEqualTo(access);
        assertThat(newRefresh).isNotEqualTo(refresh);

        bearer(access).get("/api/v1/users/me").then().statusCode(401);
        bearer(newAccess).get("/api/v1/users/me").then().statusCode(200);
    }

    @Test
    void reusedRefreshTokenWithinGraceIsRejectedButKeepsTheSession() {
        Response login = apiSignIn();
        String refresh = login.jsonPath().getString("refreshToken");
        String newAccess = refresh(refresh).jsonPath().getString("accessToken");

        Response reused = refresh(refresh);
        assertThat(reused.statusCode()).isEqualTo(400);
        assertThat(reused.jsonPath().getString("code")).isEqualTo("INVALID_REFRESH_TOKEN");
        bearer(newAccess).get("/api/v1/users/me").then().statusCode(200);
    }

    @Test
    void reusedRefreshTokenAfterGraceRevokesTheSession() {
        Response login = apiSignIn();
        String refresh = login.jsonPath().getString("refreshToken");
        Response refreshed = refresh(refresh);
        String newAccess = refreshed.jsonPath().getString("accessToken");
        String newRefresh = refreshed.jsonPath().getString("refreshToken");

        QuarkusTransaction.requiringNew().run(() -> sessionRepository
                .update("refreshRotatedAt = ?1 WHERE user.id = ?2", Instant.now().minusSeconds(300), actor.userId()));

        assertThat(refresh(refresh).statusCode()).isEqualTo(400);
        bearer(newAccess).get("/api/v1/users/me").then().statusCode(401);
        assertThat(refresh(newRefresh).statusCode()).isEqualTo(400);
        assertThat(revokedReasons()).contains(SessionRevocationReason.REFRESH_TOKEN_REUSE);
    }

    @Test
    void passwordChangeSignsOutOtherSessionsButKeepsTheCurrentOne() {
        String current = browserSignIn();
        String other = browserSignIn();
        String api = apiSignIn().jsonPath().getString("accessToken");

        given().cookie(SESSION_COOKIE, current)
                .contentType(ContentType.JSON)
                .body(Map.of("oldPassword", actor.password(), "newPassword", "a-new-password-123"))
                .when().put("/api/v1/users/me/password")
                .then().statusCode(200);

        given().cookie(SESSION_COOKIE, current).when().get("/api/v1/users/me").then().statusCode(200);
        given().cookie(SESSION_COOKIE, other).when().get("/api/v1/users/me").then().statusCode(401);
        bearer(api).get("/api/v1/users/me").then().statusCode(401);
    }

    @Test
    void disablingAUserEndsAllOfItsSessions() {
        String session = browserSignIn();
        String api = apiSignIn().jsonPath().getString("accessToken");

        adminUserService.setUserStatus(actor.userId(), false);

        given().cookie(SESSION_COOKIE, session).when().get("/api/v1/users/me").then().statusCode(401);
        bearer(api).get("/api/v1/users/me").then().statusCode(401);
        assertThat(revokedReasons()).containsOnly(SessionRevocationReason.USER_DISABLED);
    }

    @Test
    void apiSessionLogoutRevokesTheBearerSession() {
        String api = apiSignIn().jsonPath().getString("accessToken");

        bearer(api).delete("/api/v1/auth/sessions/current").then().statusCode(200);

        bearer(api).get("/api/v1/users/me").then().statusCode(401);
    }

    @Test
    void retiredJwtBearerTokensAreRejectedWithTheProblemEnvelope() {
        Response response = bearer("eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ4In0.c2lnbmF0dXJl").get("/api/v1/users/me");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("code")).isEqualTo("AUTHENTICATION_REQUIRED");
    }

    private String browserSignIn() {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", actor.email(), "password", actor.password()))
                .when().post("/api/v1/auth/sessions")
                .then().statusCode(200)
                .extract().cookie(SESSION_COOKIE);
    }

    private Response apiSignIn() {
        Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", actor.email(), "password", actor.password()))
                .when().post("/api/v1/auth/api-sessions");
        assertThat(response.statusCode()).isEqualTo(200);
        return response;
    }

    private Response refresh(String refreshToken) {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("refreshToken", refreshToken))
                .when().post("/api/v1/auth/api-sessions/current/refresh");
    }

    private io.restassured.specification.RequestSpecification bearer(String token) {
        return given().header("Authorization", "Bearer " + token).when();
    }

    private java.util.List<SessionRevocationReason> revokedReasons() {
        return QuarkusTransaction.requiringNew().call(() -> sessionRepository
                .find("user.id = ?1 AND revokedAt IS NOT NULL", actor.userId())
                .list().stream()
                .map(UserSessionEntity::getRevokedReason)
                .toList());
    }
}
