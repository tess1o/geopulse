package org.github.tess1o.geopulse.auth.service;

import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.auth.session.model.SessionAuthMethod;
import org.github.tess1o.geopulse.auth.session.model.SessionContext;
import org.github.tess1o.geopulse.shared.api.UserIpAddress;

/** Describes a new session with the user agent and IP address of the current request. */
@RequestScoped
public class SessionContextFactory {

    @Inject
    HttpServerRequest request;

    public SessionContext context(SessionAuthMethod authMethod) {
        return SessionContext.of(authMethod, request.getHeader("User-Agent"), UserIpAddress.resolve(request));
    }

    public SessionContext oidcContext(String provider, String subject, String sessionId, String idToken) {
        return SessionContext.oidc(provider, subject, sessionId, idToken,
                request.getHeader("User-Agent"), UserIpAddress.resolve(request));
    }
}
