package org.github.tess1o.geopulse.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.auth.dto.CreateApiTokenResponse;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.model.UserApiTokenEntity;
import org.github.tess1o.geopulse.auth.service.ApiTokenSecretService;
import org.github.tess1o.geopulse.auth.service.ApiTokenService;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
class ApiTokenAuthenticationTest {

    @Inject
    UserService userService;

    @Inject
    AuthenticationService authenticationService;

    @Inject
    ApiTokenService apiTokenService;

    @Inject
    ApiTokenSecretService apiTokenSecretService;

    private UUID userId;
    private String accessToken;
    private String apiToken;
    private UUID apiTokenId;

    @BeforeEach
    @Transactional
    void setup() {
        String email = TestIds.uniqueEmail("api-token-user");
        UserEntity user = userService.registerUser(email, "password123", "API Token User", "UTC");
        userId = user.getId();

        AuthResponse authResponse = authenticationService.authenticate(email, "password123");
        accessToken = authResponse.getAccessToken();

        CreateApiTokenResponse tokenResponse = apiTokenService.createToken(
                userId,
                "Test automation",
                Instant.now().plusSeconds(3600),
                "127.0.0.1"
        );
        apiToken = tokenResponse.getToken();
        apiTokenId = tokenResponse.getApiToken().getId();
    }

    @Test
    void protectedEndpointAcceptsApiTokenHeader() {
        given()
                .contentType(ContentType.JSON)
                .header("X-API-Key", apiToken)
                .when()
                .get("/api/users/me")
                .then()
                .statusCode(200)
                .body("status", equalTo("success"))
                .body("data.userId", equalTo(userId.toString()))
                .body("data.email", containsString("api-token-user"));
    }

    @Test
    void protectedEndpointAcceptsBearerServiceToken() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + apiToken)
                .when()
                .get("/api/friends")
                .then()
                .statusCode(200)
                .body("status", equalTo("success"));
    }

    @Test
    void revokedTokenIsRejected() {
        apiTokenService.revokeOwnedToken(userId, apiTokenId, "127.0.0.1");

        given()
                .contentType(ContentType.JSON)
                .header("X-API-Key", apiToken)
                .when()
                .get("/api/users/me")
                .then()
                .statusCode(401)
                .contentType(ContentType.JSON)
                .body("status", equalTo("error"))
                .body("message", equalTo("Invalid service account token"))
                .body("data", nullValue());
    }

    @Test
    void expiredTokenIsRejected() {
        CreateApiTokenResponse tokenResponse = createExpiredToken();

        given()
                .contentType(ContentType.JSON)
                .header("X-API-Key", tokenResponse.getToken())
                .when()
                .get("/api/users/me")
                .then()
                .statusCode(401)
                .contentType(ContentType.JSON)
                .body("status", equalTo("error"))
                .body("message", equalTo("Invalid service account token"))
                .body("data", nullValue());
    }

    @Test
    void invalidServiceTokenReturnsJsonError() {
        given()
                .contentType(ContentType.JSON)
                .header("X-API-Key", ApiTokenSecretService.TOKEN_PREFIX + "invalid")
                .when()
                .get("/api/users/me")
                .then()
                .statusCode(401)
                .contentType(ContentType.JSON)
                .body("status", equalTo("error"))
                .body("message", equalTo("Invalid service account token"))
                .body("data", nullValue());
    }

    @Test
    void malformedApiKeyHeaderReturnsJsonError() {
        given()
                .contentType(ContentType.JSON)
                .header("X-API-Key", "1" + ApiTokenSecretService.TOKEN_PREFIX + "invalid")
                .when()
                .get("/api/users/me")
                .then()
                .statusCode(401)
                .contentType(ContentType.JSON)
                .body("status", equalTo("error"))
                .body("message", equalTo("Invalid service account token"))
                .body("data", nullValue());
    }

    @Test
    void regularUserApiTokenCannotAccessAdminEndpoints() {
        given()
                .contentType(ContentType.JSON)
                .header("X-API-Key", apiToken)
                .when()
                .get("/api/admin/users")
                .then()
                .statusCode(403);
    }

    @Test
    void adminApiTokenCanAccessAdminEndpoints() {
        String adminToken = createAdminApiToken();

        given()
                .contentType(ContentType.JSON)
                .header("X-API-Key", adminToken)
                .when()
                .get("/api/admin/users")
                .then()
                .statusCode(200)
                .body("content", notNullValue());
    }

    @Test
    void userCanCreateAndListTokenThroughApi() {
        String tokenId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body("""
                        {
                          "name": "Created through API",
                          "expiresAt": "%s"
                        }
                        """.formatted(Instant.now().plusSeconds(7200)))
                .when()
                .post("/api/api-tokens")
                .then()
                .statusCode(201)
                .body("status", equalTo("success"))
                .body("data.token", startsWith(ApiTokenSecretService.TOKEN_PREFIX))
                .body("data.apiToken.name", equalTo("Created through API"))
                .body("data.apiToken.preview", startsWith(ApiTokenSecretService.TOKEN_PREFIX))
                .extract()
                .path("data.apiToken.id");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .get("/api/api-tokens")
                .then()
                .statusCode(200)
                .body("data.id", hasItem(tokenId));
    }

    @Transactional
    CreateApiTokenResponse createExpiredToken() {
        CreateApiTokenResponse tokenResponse = apiTokenService.createToken(
                userId,
                "Expired token",
                Instant.now().plusSeconds(60),
                "127.0.0.1"
        );
        String hash = apiTokenSecretService.hashToken(tokenResponse.getToken());
        UserApiTokenEntity entity = UserApiTokenEntity.find("tokenHash", hash).firstResult();
        entity.setExpiresAt(Instant.now().minusSeconds(60));
        entity.persist();
        return tokenResponse;
    }

    @Transactional
    String createAdminApiToken() {
        String email = TestIds.uniqueEmail("api-token-admin");
        UserEntity admin = userService.registerUser(email, "password123", "API Token Admin", "UTC");
        userService.updateRole(admin.getId(), Role.ADMIN);
        return apiTokenService.createToken(
                admin.getId(),
                "Admin automation",
                Instant.now().plusSeconds(3600),
                "127.0.0.1"
        ).getToken();
    }
}
