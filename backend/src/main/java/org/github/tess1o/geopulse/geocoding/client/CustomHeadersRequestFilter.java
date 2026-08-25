package org.github.tess1o.geopulse.geocoding.client;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;

import java.io.IOException;
import java.util.Map;

public class CustomHeadersRequestFilter implements ClientRequestFilter {

    private final Map<String, String> headers;

    public CustomHeadersRequestFilter(Map<String, String> headers) {
        this.headers = headers == null ? Map.of() : headers;
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        headers.forEach((key, value) -> {
            if (key != null && value != null && !key.isBlank() && !value.isBlank()) {
                requestContext.getHeaders().putSingle(key, value);
            }
        });
    }
}
