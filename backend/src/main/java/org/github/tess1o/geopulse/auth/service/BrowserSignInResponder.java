package org.github.tess1o.geopulse.auth.service;

import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.auth.session.model.SessionContext;
import org.github.tess1o.geopulse.auth.session.model.SessionRevocationReason;
import org.github.tess1o.geopulse.auth.session.service.UserSessionService;
import org.github.tess1o.geopulse.user.model.UserEntity;

/**
 * Builds the response of every browser sign-in: starts a session, sets the session cookie and
 * returns the signed-in user. A session the browser already carried is revoked, so signing in
 * again never leaves an orphaned session behind.
 */
@RequestScoped
public class BrowserSignInResponder {

    @Inject
    HttpServerRequest request;

    @Inject
    AuthenticationService authenticationService;

    @Inject
    UserSessionService userSessionService;

    @Inject
    CookieService cookieService;

    @Inject
    BrowserAuthResponseMapper browserAuthResponseMapper;

    public Response signIn(UserEntity user, SessionContext context, String redirectUri) {
        Cookie previous = request.getCookie(CookieService.SESSION_COOKIE);
        if (previous != null) {
            userSessionService.revokeByToken(previous.getValue(), SessionRevocationReason.REPLACED);
        }

        AuthenticationService.BrowserSignIn signIn = authenticationService.createBrowserSession(user, context);
        return Response.ok(browserAuthResponseMapper.toBrowserAuthResponse(signIn.profile(), redirectUri))
                .cookie(cookieService.createSessionCookie(signIn.session().secret(),
                        signIn.session().cookieMaxAgeSeconds()))
                .build();
    }
}
