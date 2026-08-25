package org.github.tess1o.geopulse.geocoding.client;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
class CustomHeadersRequestFilterTest {

    @Test
    void filter_shouldApplyConfiguredHeaders() throws IOException {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        ClientRequestContext context = mock(ClientRequestContext.class);
        when(context.getHeaders()).thenReturn(headers);

        CustomHeadersRequestFilter filter = new CustomHeadersRequestFilter(Map.of(
                "X-Api-Key", "secret",
                "Accept-Language", "en"
        ));

        filter.filter(context);

        assertThat(headers.getFirst("X-Api-Key")).isEqualTo("secret");
        assertThat(headers.getFirst("Accept-Language")).isEqualTo("en");
    }

    @Test
    void filter_shouldIgnoreEmptyHeaderMap() throws IOException {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        ClientRequestContext context = mock(ClientRequestContext.class);
        when(context.getHeaders()).thenReturn(headers);

        CustomHeadersRequestFilter filter = new CustomHeadersRequestFilter(Map.of());

        filter.filter(context);

        assertThat(headers).isEmpty();
    }
}
