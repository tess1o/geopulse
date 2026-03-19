package org.github.tess1o.geopulse.geofencing.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geofencing.util.NotificationDestinationParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;

@ApplicationScoped
@Slf4j
public class AppriseHttpClient {

    private final ObjectMapper objectMapper;

    @Inject
    public AppriseHttpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AppriseClientResult ping(String baseUrl, String authToken, int timeoutMs, boolean verifyTls) {
        try {
            URI uri = normalizeBaseUrl(baseUrl);
            HttpClient client = buildHttpClient(timeoutMs, verifyTls);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(Duration.ofMillis(timeoutMs));

            if (authToken != null && !authToken.isBlank()) {
                requestBuilder.header("X-API-Key", authToken.trim());
            }

            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 500 && status != 401 && status != 403) {
                return new AppriseClientResult(true, status, "Apprise endpoint is reachable");
            }
            return new AppriseClientResult(false, status, "Apprise test request failed with HTTP " + status);
        } catch (Exception e) {
            if (isUnexpectedContentLengthOn204(e)) {
                log.info("Apprise ping returned HTTP 204 with invalid content-length header; treating as reachable");
                return new AppriseClientResult(true, 204, "Apprise endpoint is reachable");
            }
            String detail = buildErrorMessage(e, "Apprise ping failed");
            log.warn("Apprise ping failed: {}", detail);
            return new AppriseClientResult(false, 0, detail);
        }
    }

    public AppriseClientResult notify(String baseUrl,
                                      String authToken,
                                      int timeoutMs,
                                      boolean verifyTls,
                                      String destination,
                                      String title,
                                      String body) {
        try {
            URI endpoint = normalizeNotifyUrl(baseUrl);
            HttpClient client = buildHttpClient(timeoutMs, verifyTls);

            List<String> urls = NotificationDestinationParser.parseUrls(destination);
            if (urls.isEmpty()) {
                return new AppriseClientResult(false, 0, "No destination URLs provided");
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("urls", urls);
            payload.put("title", title != null ? title : "GeoPulse");
            payload.put("body", body != null ? body : "GeoPulse geofence event");
            payload.put("format", "text");
            payload.put("type", "info");

            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload));

            if (authToken != null && !authToken.isBlank()) {
                requestBuilder.header("X-API-Key", authToken.trim());
            }

            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return new AppriseClientResult(true, status, "Delivered");
            }

            String bodyText = response.body() == null ? "" : response.body();
            if (bodyText.length() > 240) {
                bodyText = bodyText.substring(0, 240) + "...";
            }
            return new AppriseClientResult(false, status, "HTTP " + status + ": " + bodyText);
        } catch (Exception e) {
            if (isUnexpectedContentLengthOn204(e)) {
                log.info("Apprise notify returned HTTP 204 with invalid content-length header; treating as delivered");
                return new AppriseClientResult(true, 204, "Delivered");
            }
            String detail = buildErrorMessage(e, "Apprise notify failed");
            log.warn("Apprise notify failed: {}", detail);
            return new AppriseClientResult(false, 0, detail);
        }
    }

    private String buildErrorMessage(Throwable error, String fallback) {
        if (error == null) {
            return fallback;
        }

        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                return message;
            }
            current = current.getCause();
        }

        String type = error.getClass().getSimpleName();
        return fallback + (type == null || type.isBlank() ? "" : " (" + type + ")");
    }

    private boolean isUnexpectedContentLengthOn204(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("unexpected content length header with 204 response")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private URI normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Apprise API URL is not configured");
        }
        String normalized = url.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://" + normalized;
        }
        return URI.create(normalized.endsWith("/") ? normalized : normalized + "/");
    }

    private URI normalizeNotifyUrl(String baseUrl) {
        URI base = normalizeBaseUrl(baseUrl);
        String raw = base.toString();
        if (raw.endsWith("/notify")) {
            return base;
        }
        if (raw.endsWith("/notify/")) {
            return URI.create(raw.substring(0, raw.length() - 1));
        }
        return URI.create(raw + "notify");
    }

    private HttpClient buildHttpClient(int timeoutMs, boolean verifyTls) throws Exception {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs));

        if (!verifyTls) {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new SecureRandom());
            SSLParameters sslParameters = new SSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm(null);

            builder.sslContext(sslContext);
            builder.sslParameters(sslParameters);
        }

        return builder.build();
    }
}
