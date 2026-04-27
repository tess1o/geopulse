package org.github.tess1o.geopulse.immich.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.immich.model.ImmichAsset;
import org.github.tess1o.geopulse.immich.model.ImmichSearchRequest;
import org.github.tess1o.geopulse.immich.model.ImmichSearchResponse;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
@Slf4j
public class ImmichClient {
    private static final int DEFAULT_PAGE_SIZE = 500;
    private static final int MAX_PAGES = 1000;

    @Inject
    Vertx vertx;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "immich.photos.search-timeout-seconds", defaultValue = "15")
    int searchTimeoutSeconds;

    public CompletableFuture<ImmichSearchResponse> searchAssetsAllPages(String baseUrl, String apiKey, ImmichSearchRequest request) {
        return searchAssets(baseUrl, apiKey, request)
                .thenCompose(firstPage -> {
                    List<ImmichAsset> allItems = new ArrayList<>(safeItems(firstPage));
                    return fetchRemainingPages(baseUrl, apiKey, request, firstPage, allItems, 0)
                            .thenApply(ignored -> buildMergedResponse(firstPage, allItems));
                });
    }

    public CompletableFuture<ImmichSearchResponse> searchAssets(String baseUrl, String apiKey, ImmichSearchRequest request) {
        CompletableFuture<ImmichSearchResponse> future = new CompletableFuture<>();
        long requestTimeoutMs = Math.max(searchTimeoutSeconds, 1) * 1000L;

        try {
            WebClient client = WebClient.create(vertx);
            URI uri = URI.create(baseUrl);

            String requestBody = objectMapper.writeValueAsString(request);

            client
                    .post(uri.getPort() == -1 ? (uri.getScheme().equals("https") ? 443 : 80) : uri.getPort(),
                            uri.getHost(), "/api/search/metadata")
                    .ssl(uri.getScheme().equals("https"))
                    .timeout(requestTimeoutMs)
                    .putHeader("Content-Type", "application/json")
                    .putHeader("x-api-key", apiKey)
                    .sendBuffer(Buffer.buffer(requestBody))
                    .onComplete(ar -> {
                        client.close();
                        if (ar.succeeded()) {
                            HttpResponse<Buffer> response = ar.result();
                            if (response.statusCode() != 200) {
                                future.completeExceptionally(new RuntimeException("Immich API error: " + response.statusCode() + " - " + response.bodyAsString()));
                                return;
                            }
                            try {
                                ImmichSearchResponse searchResponse = objectMapper.readValue(response.bodyAsString(), ImmichSearchResponse.class);
                                future.complete(searchResponse);
                            } catch (Exception e) {
                                future.completeExceptionally(new RuntimeException("Failed to parse Immich response", e));
                            }
                        } else {
                            future.completeExceptionally(ar.cause());
                        }
                    });

        } catch (Exception e) {
            log.error("Failed to search assets from Immich server {}: {}", baseUrl, e.getMessage(), e);
            future.completeExceptionally(e);
        }

        return future;
    }

    private CompletableFuture<Void> fetchRemainingPages(
            String baseUrl,
            String apiKey,
            ImmichSearchRequest originalRequest,
            ImmichSearchResponse currentResponse,
            List<ImmichAsset> allItems,
            int depth
    ) {
        if (depth >= MAX_PAGES) {
            log.warn("Reached max Immich pagination depth ({}) for request {}", MAX_PAGES, originalRequest);
            return CompletableFuture.completedFuture(null);
        }

        Integer nextPage = resolveNextPage(currentResponse, originalRequest, allItems.size());
        if (nextPage == null) {
            return CompletableFuture.completedFuture(null);
        }

        ImmichSearchRequest nextRequest = copyRequestForNextPage(originalRequest, nextPage, resolvePageSize(currentResponse, originalRequest));
        return searchAssets(baseUrl, apiKey, nextRequest)
                .thenCompose(nextResponse -> {
                    List<ImmichAsset> nextItems = safeItems(nextResponse);
                    if (nextItems.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    allItems.addAll(nextItems);
                    return fetchRemainingPages(baseUrl, apiKey, originalRequest, nextResponse, allItems, depth + 1);
                });
    }

    private ImmichSearchResponse buildMergedResponse(
            ImmichSearchResponse firstPage,
            List<ImmichAsset> allItems
    ) {
        ImmichSearchResponse merged = new ImmichSearchResponse();
        ImmichSearchResponse.ImmichSearchAssets mergedAssets = new ImmichSearchResponse.ImmichSearchAssets();

        ImmichSearchResponse.ImmichSearchAssets firstAssets = firstPage != null ? firstPage.getAssets() : null;
        int total = firstAssets != null && firstAssets.getTotal() != null
                ? firstAssets.getTotal()
                : allItems.size();

        mergedAssets.setTotal(total);
        mergedAssets.setCount(allItems.size());
        mergedAssets.setItems(allItems);
        merged.setAssets(mergedAssets);
        return merged;
    }

    private List<ImmichAsset> safeItems(ImmichSearchResponse response) {
        if (response == null || response.getAssets() == null || response.getAssets().getItems() == null) {
            return List.of();
        }
        return response.getAssets().getItems();
    }

    private Integer resolveNextPage(
            ImmichSearchResponse response,
            ImmichSearchRequest originalRequest,
            int fetchedCount
    ) {
        if (response == null || response.getAssets() == null) {
            return null;
        }

        ImmichSearchResponse.ImmichSearchAssets assets = response.getAssets();
        if (assets.getNextPage() != null) {
            return assets.getNextPage();
        }

        Integer total = assets.getTotal();
        if (total == null || fetchedCount >= total) {
            return null;
        }

        int currentPage = assets.getPage() != null
                ? assets.getPage()
                : (originalRequest.getPage() != null ? originalRequest.getPage() : 1);

        return currentPage + 1;
    }

    private int resolvePageSize(ImmichSearchResponse response, ImmichSearchRequest originalRequest) {
        if (originalRequest.getSize() != null && originalRequest.getSize() > 0) {
            return originalRequest.getSize();
        }
        if (response != null
                && response.getAssets() != null
                && response.getAssets().getCount() != null
                && response.getAssets().getCount() > 0) {
            return response.getAssets().getCount();
        }
        return DEFAULT_PAGE_SIZE;
    }

    private ImmichSearchRequest copyRequestForNextPage(ImmichSearchRequest request, int nextPage, int pageSize) {
        return ImmichSearchRequest.builder()
                .takenAfter(request.getTakenAfter())
                .takenBefore(request.getTakenBefore())
                .type(request.getType())
                .city(request.getCity())
                .country(request.getCountry())
                .withExif(request.isWithExif())
                .page(nextPage)
                .size(pageSize)
                .build();
    }

    public CompletableFuture<byte[]> getThumbnail(String baseUrl, String apiKey, String assetId) {
        return getAssetBytes(baseUrl, apiKey, assetId, "/api/assets/" + assetId + "/thumbnail");
    }

    public CompletableFuture<byte[]> getOriginal(String baseUrl, String apiKey, String assetId) {
        return getAssetBytes(baseUrl, apiKey, assetId, "/api/assets/" + assetId + "/original");
    }

    private CompletableFuture<byte[]> getAssetBytes(String baseUrl, String apiKey, String assetId, String path) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();

        try {
            WebClient client = WebClient.create(vertx);
            URI uri = URI.create(baseUrl);

            client
                    .get(uri.getPort() == -1 ? (uri.getScheme().equals("https") ? 443 : 80) : uri.getPort(),
                            uri.getHost(), path)
                    .ssl(uri.getScheme().equals("https"))
                    .putHeader("x-api-key", apiKey)
                    .send()
                    .onComplete(ar -> {
                        client.close();
                        if (ar.succeeded()) {
                            HttpResponse<Buffer> response = ar.result();
                            if (response.statusCode() == 200) {
                                future.complete(response.bodyAsBuffer().getBytes());
                            } else {
                                future.completeExceptionally(new RuntimeException("Failed to get asset: " + response.statusCode()));
                            }
                        } else {
                            log.error("Failed to get asset {} from Immich server {}: {}", assetId, baseUrl, ar.cause().getMessage(), ar.cause());
                            future.completeExceptionally(ar.cause());
                        }
                    });

        } catch (Exception e) {
            log.error("Failed to get asset {} from Immich server {}: {}", assetId, baseUrl, e.getMessage(), e);
            future.completeExceptionally(e);
        }

        return future;
    }
}
