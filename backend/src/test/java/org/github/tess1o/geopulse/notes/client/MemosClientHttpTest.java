package org.github.tess1o.geopulse.notes.client;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.notes.model.MemosCreateMemoRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
class MemosClientHttpTest {
    private HttpServer server;
    private MemosClient client;
    private String baseUrl;
    private final List<String> requests = new ArrayList<>();
    private final List<String> queries = new ArrayList<>();
    private int modernStatus = 200;
    private int postStatus = 200;

    @BeforeEach
    void startServer() throws Exception {
        client = new MemosClient();
        client.searchTimeoutSeconds = 2;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/nested", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            requests.add(method + " " + path);
            queries.add(exchange.getRequestURI().getRawQuery());
            int status = path.equals("/nested/api/memos") ? modernStatus : 200;
            String body = "{\"memos\":[{\"name\":\"memos/first\"}],\"nextPageToken\":\"next\"}";
            if ("POST".equals(method)) {
                exchange.getRequestBody().readAllBytes();
                status = postStatus;
                body = "{\"name\":\"memos/created\"}";
            } else if (exchange.getRequestURI().getRawQuery().contains("pageToken=next")) {
                body = "{\"memos\":[{\"name\":\"memos/second\"}]}";
            }
            if (status != 200) {
                body = "{\"message\":\"upstream error\"}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/nested///";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void readsModernApiAndKeepsPaginationOnSelectedRoute() {
        var memos = client.listMemosAllPages(baseUrl, "token", Instant.EPOCH, Instant.EPOCH, 2, List.of("work"));
        assertEquals(List.of("memos/first", "memos/second"), memos.stream().map(memo -> memo.getName()).toList());
        assertEquals(List.of("GET /nested/api/memos", "GET /nested/api/memos"), requests);
        assertTrue(queries.get(1).contains("pageToken=next"));
        assertTrue(queries.get(0).contains("filter="));
        assertTrue(queries.get(0).contains("orderBy="));
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 410})
    void fallsBackToLegacyAndKeepsPaginationOnLegacyRoute(int status) {
        modernStatus = status;
        assertEquals(2, client.listMemosAllPages(baseUrl, "token", null, null, 2, List.of()).size());
        assertEquals(List.of("GET /nested/api/memos", "GET /nested/api/v1/memos",
                "GET /nested/api/v1/memos"), requests);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 500})
    void preservesErrorsWithoutFallback(int status) {
        modernStatus = status;
        var error = assertThrows(IllegalStateException.class,
                () -> client.fetchMemosPage(baseUrl, "token", 1, null, null));
        assertTrue(error.getMessage().contains("Memos API error: " + status));
        assertEquals(List.of("GET /nested/api/memos"), requests);
    }

    @Test
    void connectionTestCanUseLegacyRoute() {
        modernStatus = 404;
        assertEquals("memos/first", client.fetchMemosPage(baseUrl, "token", 1, null, null).getMemos().getFirst().getName());
        assertEquals(List.of("GET /nested/api/memos", "GET /nested/api/v1/memos"), requests);
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 404, 410})
    void resolvesRouteBeforeCreatingExactlyOnce(int status) {
        modernStatus = status;
        var memo = client.createMemo(baseUrl, "token", MemosCreateMemoRequest.builder().content("hello").build());
        assertEquals("memos/created", memo.getName());
        assertEquals(status == 200
                ? List.of("GET /nested/api/memos", "POST /nested/api/memos")
                : List.of("GET /nested/api/memos", "GET /nested/api/v1/memos", "POST /nested/api/v1/memos"), requests);
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 410, 500})
    void doesNotRetryFailedPost(int status) {
        postStatus = status;
        assertThrows(IllegalStateException.class,
                () -> client.createMemo(baseUrl, "token", MemosCreateMemoRequest.builder().content("hello").build()));
        assertEquals(List.of("GET /nested/api/memos", "POST /nested/api/memos"), requests);
    }

}
