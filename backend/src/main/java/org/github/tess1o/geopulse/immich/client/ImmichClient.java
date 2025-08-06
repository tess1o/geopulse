package org.github.tess1o.geopulse.immich.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.immich.model.ImmichSearchRequest;
import org.github.tess1o.geopulse.immich.model.ImmichSearchResponse;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
@Slf4j
public class ImmichClient {

    @Inject
    Vertx vertx;

    @Inject
    ObjectMapper objectMapper;

    public CompletableFuture<ImmichSearchResponse> searchAssets(String baseUrl, String apiKey, ImmichSearchRequest request) {
        CompletableFuture<ImmichSearchResponse> future = new CompletableFuture<>();
        
        try {
            WebClient client = WebClient.create(vertx);
            URI uri = URI.create(baseUrl);
            
            String requestBody = objectMapper.writeValueAsString(request);
            
            client
                    .post(uri.getPort() == -1 ? (uri.getScheme().equals("https") ? 443 : 80) : uri.getPort(), 
                          uri.getHost(), "/api/search/metadata")
                    .ssl(uri.getScheme().equals("https"))
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