package org.github.tess1o.geopulse.mcp;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.dto.CreateApiTokenResponse;
import org.github.tess1o.geopulse.auth.service.ApiTokenService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
public class McpAuthenticationTest {

    @Inject
    UserService userService;

    @Inject
    ApiTokenService apiTokenService;

    private String apiToken;

    @BeforeEach
    @Transactional
    void setup() {
        UserEntity user = userService.registerUser(TestIds.uniqueEmail("mcp-user"), "password123", "MCP User", "UTC");
        UUID userId = user.getId();
        CreateApiTokenResponse token = apiTokenService.createToken(
                userId,
                "MCP test",
                Instant.now().plusSeconds(3600),
                "127.0.0.1"
        );
        apiToken = token.getToken();
    }

    @Test
    void mcpEndpointRequiresAuthentication() {
        given()
                .contentType(ContentType.JSON)
                .accept("application/json, text/event-stream")
                .body(initializeRequest())
                .when()
                .post("/mcp")
                .then()
                .statusCode(401);
    }

    @Test
    void mcpEndpointAcceptsApiTokenBearerAuth() {
        given()
                .contentType(ContentType.JSON)
                .accept("application/json, text/event-stream")
                .header("Authorization", "Bearer " + apiToken)
                .body(initializeRequest())
                .when()
                .post("/mcp")
                .then()
                .statusCode(200);
    }

    private String initializeRequest() {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "method": "initialize",
                  "params": {
                    "protocolVersion": "2025-11-25",
                    "capabilities": {},
                    "clientInfo": {"name": "test", "version": "1.0"}
                  }
                }
                """;
    }
}
