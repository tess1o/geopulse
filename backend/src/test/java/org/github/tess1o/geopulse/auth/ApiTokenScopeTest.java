package org.github.tess1o.geopulse.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.service.ApiTokenService;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
class ApiTokenScopeTest {

    @Inject
    UserService userService;

    @Inject
    AuthenticationService authenticationService;

    @Inject
    ApiTokenService apiTokenService;

    private String sessionToken;
    private String fullToken;
    private String readToken;
    private String writeToken;
    private String ingestToken;
    private String exportToken;
    private String importToken;

    @BeforeEach
    @Transactional
    void setUp() {
        String email = TestIds.uniqueEmail("api-token-scopes");
        UserEntity user = userService.registerUser(email, "password123", "Scoped Token User", "UTC");
        AuthResponse session = authenticationService.authenticate(email, "password123");
        sessionToken = session.getAccessToken();

        Instant expires = Instant.now().plusSeconds(3600);
        fullToken = apiTokenService.createToken(user.getId(), "legacy", expires, "127.0.0.1").getToken();
        readToken = token(user, "read", List.of("read"), expires);
        writeToken = token(user, "write", List.of("read", "write"), expires);
        ingestToken = token(user, "ingest", List.of("gps:ingest"), expires);
        exportToken = token(user, "export", List.of("export"), expires);
        importToken = token(user, "import", List.of("import"), expires);
    }

    @Test
    void readTokenCanReadButNotWrite() {
        given().header("X-API-Key", readToken)
                .when().get("/api/v1/friends")
                .then().statusCode(200);

        assertScopeDenied(given().header("X-API-Key", readToken)
                .contentType(ContentType.JSON)
                .body(homeAssistantSource())
                .when().post("/api/v1/gps/sources/"), "write");

        given().header("X-API-Key", writeToken)
                .contentType(ContentType.JSON)
                .body(homeAssistantSource())
                .when().post("/api/v1/gps/sources/")
                .then().statusCode(201);
    }

    @Test
    void tokenScopesDoNotRestrictSessions() {
        given().header("Authorization", "Bearer " + sessionToken)
                .contentType(ContentType.JSON)
                .body(homeAssistantSource())
                .when().post("/api/v1/gps/sources/")
                .then().statusCode(201);
    }

    @Test
    void fullAccessTokensKeepWorkingEverywhere() {
        given().header("X-API-Key", fullToken)
                .when().get("/api/v1/api-tokens")
                .then().statusCode(200);

        given().header("X-API-Key", fullToken)
                .contentType(ContentType.JSON)
                .body(homeAssistantSource())
                .when().post("/api/v1/gps/sources/")
                .then().statusCode(201);
    }

    @Test
    void accountSecurityPathsNeedFullAccess() {
        assertScopeDenied(given().header("X-API-Key", writeToken)
                .when().get("/api/v1/api-tokens"), "all");

        assertScopeDenied(given().header("X-API-Key", writeToken)
                .contentType(ContentType.JSON)
                .body(Map.of("name", "escalated", "scopes", List.of("all")))
                .when().post("/api/v1/api-tokens"), "all");
    }

    @Test
    void anyTokenCanDescribeItself() {
        given().header("Authorization", "Bearer " + ingestToken)
                .when().get("/api/v1/api-tokens/current")
                .then()
                .statusCode(200)
                .body("name", equalTo("ingest"))
                .body("scopes", contains("gps:ingest"));

        given().header("X-API-Key", fullToken)
                .when().get("/api/v1/api-tokens/current")
                .then()
                .statusCode(200)
                .body("scopes", contains("all"));

        given().header("Authorization", "Bearer " + sessionToken)
                .when().get("/api/v1/api-tokens/current")
                .then().statusCode(404);
    }

    @Test
    void ingestOnlyTokenCannotRead() {
        assertScopeDenied(given().header("X-API-Key", ingestToken)
                .when().get("/api/v1/friends"), "read");
    }

    @Test
    void exportTokenCanStartExportsWithoutWrite() {
        Response response = given().header("X-API-Key", exportToken)
                .contentType(ContentType.JSON)
                .body(Map.of("format", "geopulse",
                        "startTime", "2026-01-01T00:00:00Z",
                        "endTime", "2026-01-02T00:00:00Z"))
                .when().post("/api/v1/exports");
        assertThat(response.statusCode()).isNotEqualTo(403);

        assertScopeDenied(given().header("X-API-Key", exportToken)
                .when().get("/api/v1/friends"), "read");
    }

    @Test
    void importTokenCanOnlyAddData() {
        assertScopeDenied(given().header("X-API-Key", importToken)
                .contentType(ContentType.JSON)
                .body(Map.of("fileName", "track.gpx", "fileSize", 1024, "importFormat", "gpx",
                        "options", Map.of("clearDataBeforeImport", true)))
                .when().post("/api/v1/import-uploads"), "write");

        Response additive = given().header("X-API-Key", importToken)
                .contentType(ContentType.JSON)
                .body(Map.of("fileName", "track.gpx", "fileSize", 1024, "importFormat", "gpx"))
                .when().post("/api/v1/import-uploads");
        assertThat(additive.statusCode()).isNotEqualTo(403);
    }

    @Test
    void readOnlyPostEndpointsAcceptReadTokens() {
        Response response = given().header("X-API-Key", readToken)
                .contentType(ContentType.JSON)
                .body(Map.of("targetIds", List.of()))
                .when().post("/api/v1/map-matching-jobs/searches");
        assertThat(response.statusCode()).isNotEqualTo(403);
    }

    @Test
    void creatingTokensValidatesScopes() {
        given().header("Authorization", "Bearer " + sessionToken)
                .contentType(ContentType.JSON)
                .body(Map.of("name", "HA", "scopes", List.of("gps:ingest", "read")))
                .when().post("/api/v1/api-tokens")
                .then()
                .statusCode(201)
                .body("apiToken.scopes", contains("read", "gps:ingest"));

        assertProblemEnvelope(given().header("Authorization", "Bearer " + sessionToken)
                .contentType(ContentType.JSON)
                .body(Map.of("name", "bad", "scopes", List.of("all", "read")))
                .when().post("/api/v1/api-tokens"), 400, "API_TOKEN_INVALID");

        assertProblemEnvelope(given().header("Authorization", "Bearer " + sessionToken)
                .contentType(ContentType.JSON)
                .body(Map.of("name", "bad", "scopes", List.of("delete-everything")))
                .when().post("/api/v1/api-tokens"), 400, "API_TOKEN_INVALID");
    }

    @Test
    void limitedTokensDoNotSeeNotificationDestinations() {
        given().header("Authorization", "Bearer " + sessionToken)
                .contentType(ContentType.JSON)
                .body(Map.of("name", "Phone", "destination", "tgram://bot-secret/chat", "enabled", true,
                        "sendInApp", false, "defaultForEnter", false, "defaultForLeave", false))
                .when().post("/api/v1/geofences/templates")
                .then().statusCode(201);

        Response limited = given().header("X-API-Key", readToken).when().get("/api/v1/geofences/templates");
        assertThat(limited.statusCode()).isEqualTo(200);
        assertThat(limited.jsonPath().getList("findAll { it.name == 'Phone' }.destination")).containsOnlyNulls();

        Response full = given().header("X-API-Key", fullToken).when().get("/api/v1/geofences/templates");
        assertThat(full.jsonPath().getList("findAll { it.name == 'Phone' }.destination"))
                .containsExactly("tgram://bot-secret/chat");
    }

    @Test
    void mcpNeedsTheReadScope() {
        given().header("Authorization", "Bearer " + readToken)
                .contentType(ContentType.JSON)
                .accept("application/json, text/event-stream")
                .body(mcpInitialize())
                .when().post("/mcp")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + ingestToken)
                .contentType(ContentType.JSON)
                .accept("application/json, text/event-stream")
                .body(mcpInitialize())
                .when().post("/mcp")
                .then().statusCode(403);
    }

    private void assertScopeDenied(Response response, String requiredScope) {
        assertProblemEnvelope(response, 403, "API_TOKEN_SCOPE_REQUIRED");
        assertThat(response.jsonPath().getString("requiredScope")).isEqualTo(requiredScope);
        assertThat(response.getHeader("X-GeoPulse-Required-Scope")).isEqualTo(requiredScope);
    }

    private String token(UserEntity user, String name, List<String> scopes, Instant expires) {
        return apiTokenService.createToken(user.getId(), name, expires, scopes, "127.0.0.1").getToken();
    }

    private Map<String, Object> homeAssistantSource() {
        return Map.of("type", "HOME_ASSISTANT", "token", "ha-" + TestIds.uniqueValue("scope"),
                "connectionType", "HTTP");
    }

    private String mcpInitialize() {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "method": "initialize",
                  "params": {
                    "protocolVersion": "2025-11-25",
                    "capabilities": {},
                    "clientInfo": {"name": "scope-test", "version": "1.0"}
                  }
                }
                """;
    }
}
