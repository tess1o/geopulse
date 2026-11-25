package org.github.tess1o.geopulse.auth.oidc.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Single HTTP client for everything GeoPulse sends to OIDC providers: discovery documents, the token
 * endpoint and JWKS. Trusts the optional custom CA configured via {@link OidcTlsConfig}, and applies
 * uniform timeouts and redirect handling.
 */
@ApplicationScoped
public class OidcHttpClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /** Status code and body of a response. */
    public record Response(int status, String body) {}

    @Inject
    OidcTlsConfig tlsConfig;

    @Inject
    ObjectMapper objectMapper;

    private volatile SSLContext builtFor;
    private volatile HttpClient client;

    /** GET a URL and return the body as text. Throws {@link OidcHttpException} on non-2xx or I/O failure. */
    public String getString(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        Response response = send(request, url);
        if (response.status() < 200 || response.status() >= 300) {
            throw new OidcHttpException("GET " + url + " returned HTTP " + response.status());
        }
        return response.body();
    }

    /** GET a URL and parse the JSON body into {@code type}. */
    public <T> T getJson(String url, Class<T> type) {
        String body = getString(url);
        return parseJson(body, type);
    }

    /** POST an {@code application/x-www-form-urlencoded} form. The status is returned, not thrown on. */
    public Response postForm(String url, Map<String, String> form) {
        String encoded = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encoded))
                .build();
        return send(request, url);
    }

    public <T> T parseJson(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (IOException e) {
            throw new OidcHttpException("Invalid JSON response: " + e.getMessage(), e);
        }
    }

    private Response send(HttpRequest request, String url) {
        try {
            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OidcHttpException("Interrupted while calling " + url, e);
        } catch (IOException e) {
            throw new OidcHttpException("Request to " + url + " failed: " + e.getMessage(), e);
        }
    }

    private HttpClient httpClient() {
        SSLContext ssl = tlsConfig.getSslContext(); // null = JVM defaults
        HttpClient current = client;
        if (current != null && builtFor == ssl) {
            return current;
        }
        synchronized (this) {
            if (client == null || builtFor != ssl) {
                HttpClient.Builder builder = HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NORMAL);
                if (ssl != null) {
                    builder.sslContext(ssl);
                }
                client = builder.build();
                builtFor = ssl;
            }
            return client;
        }
    }
}
