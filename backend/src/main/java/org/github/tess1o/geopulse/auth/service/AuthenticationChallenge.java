package org.github.tess1o.geopulse.auth.service;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.github.tess1o.geopulse.shared.api.ApiErrorCode;
import org.github.tess1o.geopulse.shared.api.RequestCorrelationHandler;

import java.util.UUID;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.AUTHENTICATION_REQUIRED;

/**
 * Writes the 401 problem response shared by every GeoPulse authentication mechanism, so an
 * unauthenticated request gets the same envelope whichever mechanism Quarkus asks to challenge.
 */
final class AuthenticationChallenge {
    static final String AUTHENTICATION_REQUIRED_DETAIL = "Authentication is required";

    /** Routing-context key a mechanism sets when it rejected credentials, used as the problem detail. */
    static final String FAILURE_DETAIL = "geopulse.auth.failureDetail";

    private AuthenticationChallenge() {
    }

    static Uni<Boolean> send(RoutingContext context) {
        String detail = context.get(FAILURE_DETAIL);
        ApiErrorCode code = AUTHENTICATION_REQUIRED;
        String errorId = UUID.randomUUID().toString();
        String requestId = context.get(RequestCorrelationHandler.REQUEST_ID);
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
            RequestCorrelationHandler.attachRequestId(context, requestId);
        }
        RequestCorrelationHandler.attachErrorId(context, errorId);
        String responseBody = new JsonObject()
                .put("type", code.typeUri().toString())
                .put("title", code.title())
                .put("status", code.statusCode())
                .put("detail", detail != null ? detail : AUTHENTICATION_REQUIRED_DETAIL)
                .put("instance", context.request().path())
                .put("code", code.name())
                .put("errorId", errorId)
                .put("requestId", requestId)
                .encode();

        String finalRequestId = requestId;
        return Uni.createFrom().emitter(emitter -> context.response()
                .setStatusCode(401)
                .putHeader("WWW-Authenticate", "Bearer")
                .putHeader("Content-Type", "application/problem+json")
                .putHeader(RequestCorrelationHandler.REQUEST_ID_HEADER, finalRequestId)
                .putHeader(RequestCorrelationHandler.ERROR_ID_HEADER, errorId)
                .end(responseBody)
                .onComplete(result -> {
                    if (result.succeeded()) {
                        emitter.complete(true);
                    } else {
                        emitter.fail(result.cause());
                    }
                }));
    }
}
