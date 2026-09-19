package org.github.tess1o.geopulse.shared.api;

import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.MDC;

import java.util.UUID;

@ApplicationScoped
public class RequestCorrelationHandler {

    public static final String REQUEST_ID = "requestId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String ERROR_ID_HEADER = "X-Error-Id";

    void register(@Observes Router router) {
        router.route().order(Integer.MIN_VALUE).handler(context -> {
            String existingRequestId = context.get(REQUEST_ID);
            if (existingRequestId != null) {
                MDC.put(REQUEST_ID, existingRequestId);
                context.response().putHeader(REQUEST_ID_HEADER, existingRequestId);
                context.next();
                return;
            }
            String requestId = UUID.randomUUID().toString();
            context.put(REQUEST_ID, requestId);
            context.response().putHeader(REQUEST_ID_HEADER, requestId);
            MDC.put(REQUEST_ID, requestId);
            context.addEndHandler(ignored -> MDC.remove(REQUEST_ID));
            context.next();
        });
    }
}
