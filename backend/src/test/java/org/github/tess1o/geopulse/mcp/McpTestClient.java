package org.github.tess1o.geopulse.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A minimal MCP client over Streamable HTTP for tests: opens a session with an API token and calls tools.
 */
final class McpTestClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger requestId = new AtomicInteger(1);

    JsonNode listTools(String token) throws Exception {
        String session = initialize(token);
        return rpc(token, session, "tools/list", Map.of()).path("result").path("tools");
    }

    /** The raw {@code tools/call} result, including {@code isError}. */
    JsonNode callTool(String token, String name, Map<String, Object> arguments) throws Exception {
        String session = initialize(token);
        return rpc(token, session, "tools/call", Map.of("name", name, "arguments", arguments)).path("result");
    }

    /** The parsed result of a tool that returns one object; fails the test if the tool reported an error. */
    JsonNode callToolJson(String token, String name, Map<String, Object> arguments) throws Exception {
        JsonNode result = callTool(token, name, arguments);
        assertThat(result.path("isError").asBoolean()).as(name + ": " + text(result)).isFalse();
        return objectMapper.readTree(text(result));
    }

    /** The parsed elements of a tool that returns a list: the server sends each element as its own content item. */
    List<JsonNode> callToolItems(String token, String name, Map<String, Object> arguments) throws Exception {
        JsonNode result = callTool(token, name, arguments);
        assertThat(result.path("isError").asBoolean()).as(name + ": " + text(result)).isFalse();
        List<JsonNode> items = new ArrayList<>();
        for (JsonNode content : result.path("content")) {
            items.add(objectMapper.readTree(content.path("text").asText()));
        }
        return items;
    }

    static String text(JsonNode result) {
        return result.path("content").path(0).path("text").asText();
    }

    private String initialize(String token) throws Exception {
        Response response = post(token, null, Map.of(
                "jsonrpc", "2.0",
                "id", requestId.getAndIncrement(),
                "method", "initialize",
                "params", Map.of(
                        "protocolVersion", "2025-11-25",
                        "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "test", "version", "1.0"))));
        assertThat(response.statusCode()).isEqualTo(200);
        String session = response.header("Mcp-Session-Id");
        post(token, session, Map.of("jsonrpc", "2.0", "method", "notifications/initialized"));
        return session;
    }

    private JsonNode rpc(String token, String session, String method, Map<String, Object> params) throws Exception {
        Response response = post(token, session, Map.of(
                "jsonrpc", "2.0",
                "id", requestId.getAndIncrement(),
                "method", method,
                "params", params));
        assertThat(response.statusCode()).isEqualTo(200);
        return parseBody(response.asString());
    }

    private Response post(String token, String session, Map<String, Object> body) throws Exception {
        var request = given()
                .contentType(ContentType.JSON)
                .accept("application/json, text/event-stream")
                .header("Authorization", "Bearer " + token)
                .body(objectMapper.writeValueAsString(body));
        if (session != null) {
            request.header("Mcp-Session-Id", session);
        }
        return request.when().post("/mcp");
    }

    /** The JSON-RPC message, whether the server answered with plain JSON or a server-sent event. */
    private JsonNode parseBody(String body) throws Exception {
        String trimmed = body.trim();
        if (trimmed.startsWith("{")) {
            return objectMapper.readTree(trimmed);
        }
        StringBuilder data = new StringBuilder();
        for (String line : trimmed.split("\\R")) {
            if (line.startsWith("data:")) {
                data.append(line.substring("data:".length()).trim());
            }
        }
        return objectMapper.readTree(data.toString());
    }
}
