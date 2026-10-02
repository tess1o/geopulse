package org.github.tess1o.geopulse.shared.api;

import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.MDC;

import java.util.UUID;

@ApplicationScoped
public class RequestCorrelationHandler {

    public static final String REQUEST_ID = "requestId";
    public static final String ERROR_ID = "errorId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String ERROR_ID_HEADER = "X-Error-Id";
    private static final String CLEANUP_REGISTERED = RequestCorrelationHandler.class.getName() + ".cleanup";

    void register(@Observes Router router) {
        router.route().order(Integer.MIN_VALUE).handler(context -> {
            String existingRequestId = context.get(REQUEST_ID);
            attachRequestId(context, existingRequestId == null ? UUID.randomUUID().toString() : existingRequestId);
            context.next();
        });
    }

    public static void attachRequestId(io.vertx.ext.web.RoutingContext context, String requestId) {
        context.put(REQUEST_ID, requestId);
        context.response().putHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(REQUEST_ID, requestId);
        registerCleanup(context);
    }

    public static void attachErrorId(io.vertx.ext.web.RoutingContext context, String errorId) {
        context.put(ERROR_ID, errorId);
        MDC.put(ERROR_ID, errorId);
        registerCleanup(context);
    }

    private static void registerCleanup(io.vertx.ext.web.RoutingContext context) {
        if (context.get(CLEANUP_REGISTERED) != null) {
            return;
        }
        context.put(CLEANUP_REGISTERED, true);
        context.addEndHandler(ignored -> {
            MDC.remove(REQUEST_ID);
            MDC.remove(ERROR_ID);
        });
    }
}
