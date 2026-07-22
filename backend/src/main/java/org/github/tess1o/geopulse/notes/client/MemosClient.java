package org.github.tess1o.geopulse.notes.client;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.github.tess1o.geopulse.notes.model.MemosCreateMemoRequest;
import org.github.tess1o.geopulse.notes.model.MemosListResponse;
import org.github.tess1o.geopulse.notes.model.MemosMemo;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@Slf4j
public class MemosClient {
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 1000;
    private static final int MAX_PAGES = 1000;

    @ConfigProperty(name = "memos.notes.search-timeout-seconds", defaultValue = "15")
    int searchTimeoutSeconds;

    public List<MemosMemo> listMemosAllPages(
            String baseUrl,
            String apiKey,
            Instant startTime,
            Instant endTime,
            int requestedLimit
    ) {
        int limit = requestedLimit > 0 ? requestedLimit : MAX_PAGE_SIZE;
        int pageSize = Math.max(1, Math.min(DEFAULT_PAGE_SIZE, Math.min(limit, MAX_PAGE_SIZE)));
        String filter = buildCreatedTimeFilter(startTime, endTime);

        List<MemosMemo> allMemos = new ArrayList<>();
        MemosRestClient client = buildClient(baseUrl, apiKey);
        try {
            MemosListResponse firstPage = fetchMemosPage(client, pageSize, null, filter);
            allMemos.addAll(safeMemos(firstPage));
            if (allMemos.size() >= limit) {
                return allMemos.subList(0, limit);
            }
            return fetchRemainingPages(
                    client,
                    pageSize,
                    firstPage.getNextPageToken(),
                    filter,
                    allMemos,
                    limit,
                    0
            );
        } finally {
            closeClient(client);
        }
    }

    public MemosListResponse fetchMemosPage(
            String baseUrl,
            String apiKey,
            int pageSize,
            String pageToken,
            String filter
    ) {
        try {
            MemosRestClient client = buildClient(baseUrl, apiKey);
            try {
                return fetchMemosPage(client, pageSize, pageToken, filter);
            } finally {
                closeClient(client);
            }
        } catch (Exception e) {
            log.error("Failed to list memos from server {}: {}", baseUrl, e.getMessage(), e);
            throw asRuntimeException(e);
        }
    }

    public MemosMemo createMemo(String baseUrl, String apiKey, MemosCreateMemoRequest createRequest) {
        MemosRestClient client = null;
        try {
            client = buildClient(baseUrl, apiKey);
            try (Response response = client.createMemo(createRequest)) {
                assertSuccessful(response);
                return response.readEntity(MemosMemo.class);
            }
        } catch (Exception e) {
            log.error("Failed to create memo on server {}: {}", baseUrl, e.getMessage(), e);
            throw asRuntimeException(e);
        } finally {
            if (client != null) {
                closeClient(client);
            }
        }
    }

    private MemosListResponse fetchMemosPage(MemosRestClient client, int pageSize, String pageToken, String filter) {
        try (Response response = client.listMemos(
                Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE)),
                blankToNull(pageToken),
                blankToNull(filter),
                "create_time desc"
        )) {
            assertSuccessful(response);
            return response.readEntity(MemosListResponse.class);
        }
    }

    private List<MemosMemo> fetchRemainingPages(
            MemosRestClient client,
            int pageSize,
            String pageToken,
            String filter,
            List<MemosMemo> allMemos,
            int limit,
            int depth
    ) {
        if (pageToken == null || pageToken.isBlank() || depth >= MAX_PAGES || allMemos.size() >= limit) {
            return allMemos.size() > limit ? allMemos.subList(0, limit) : allMemos;
        }

        MemosListResponse response = fetchMemosPage(client, pageSize, pageToken, filter);
        List<MemosMemo> nextMemos = safeMemos(response);
        if (nextMemos.isEmpty()) {
            return allMemos;
        }
        allMemos.addAll(nextMemos);
        return fetchRemainingPages(client, pageSize, response.getNextPageToken(), filter, allMemos, limit, depth + 1);
    }

    private List<MemosMemo> safeMemos(MemosListResponse response) {
        if (response == null || response.getMemos() == null) {
            return List.of();
        }
        return response.getMemos();
    }

    static String buildCreatedTimeFilter(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) {
            return null;
        }
        return String.format(
                "created_ts >= %d && created_ts <= %d",
                startTime.getEpochSecond(),
                endTime.getEpochSecond()
        );
    }

    private MemosRestClient buildClient(String baseUrl, String apiKey) {
        try {
            return RestClientBuilder.newBuilder()
                    .baseUri(normalizeBaseUri(baseUrl))
                    .connectTimeout(Math.max(searchTimeoutSeconds, 1), TimeUnit.SECONDS)
                    .readTimeout(Math.max(searchTimeoutSeconds, 1), TimeUnit.SECONDS)
                    .property("microprofile.rest.client.disable.default.mapper", true)
                    .header("Authorization", "Bearer " + apiKey)
                    .build(MemosRestClient.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Memos REST client", e);
        }
    }

    private URI normalizeBaseUri(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return URI.create(normalized);
    }

    private void assertSuccessful(Response response) {
        int status = response.getStatus();
        if (status >= 200 && status < 300) {
            return;
        }
        String body = "";
        try {
            body = response.hasEntity() ? response.readEntity(String.class) : "";
        } catch (ProcessingException ignored) {
            // Keep the original HTTP status as the primary error signal.
        }
        throw new RuntimeException("Memos API error: " + status + (body == null || body.isBlank() ? "" : " - " + body));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void closeClient(MemosRestClient client) {
        if (client instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.debug("Failed to close Memos REST client", e);
            }
        }
    }

    private RuntimeException asRuntimeException(Exception e) {
        return e instanceof RuntimeException runtimeException
                ? runtimeException
                : new RuntimeException(e);
    }
}
