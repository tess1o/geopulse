package org.github.tess1o.geopulse.shared.api;

import io.vertx.core.Handler;
import io.vertx.core.AsyncResult;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.jboss.logging.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class RequestCorrelationHandlerTest {

    @AfterEach
    void clearMdc() {
        MDC.remove(RequestCorrelationHandler.REQUEST_ID);
        MDC.remove(RequestCorrelationHandler.ERROR_ID);
    }

    @Test
    void oneCompletionHandlerClearsRequestAndErrorIds() {
        Map<String, Object> contextData = new HashMap<>();
        RoutingContext context = mock(RoutingContext.class);
        HttpServerResponse response = mock(HttpServerResponse.class);
        when(context.get(anyString())).thenAnswer(invocation -> contextData.get(invocation.getArgument(0)));
        when(context.put(anyString(), org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            contextData.put(invocation.getArgument(0), invocation.getArgument(1));
            return context;
        });
        when(context.response()).thenReturn(response);
        when(response.putHeader(anyString(), anyString())).thenReturn(response);

        RequestCorrelationHandler.attachRequestId(context, "request-1");
        RequestCorrelationHandler.attachErrorId(context, "error-1");

        assertThat(MDC.get(RequestCorrelationHandler.REQUEST_ID)).isEqualTo("request-1");
        assertThat(MDC.get(RequestCorrelationHandler.ERROR_ID)).isEqualTo("error-1");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Handler<AsyncResult<Void>>> handler = ArgumentCaptor.forClass(Handler.class);
        verify(context, times(1)).addEndHandler(handler.capture());

        handler.getValue().handle(null);

        assertThat(MDC.get(RequestCorrelationHandler.REQUEST_ID)).isNull();
        assertThat(MDC.get(RequestCorrelationHandler.ERROR_ID)).isNull();
    }
}
