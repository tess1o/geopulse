package org.github.tess1o.geopulse.auth.service;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.quarkus.vertx.http.runtime.security.HttpSecurityUtils;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.Cookie;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.auth.session.service.SessionTokenService;

import java.util.Set;

/**
 * Authenticates server-side sessions: the browser session cookie, or an API session access token
 * sent as {@code Authorization: Bearer gp_at_...}. API tokens ({@code gp_sa_...} and
 * {@code X-API-Key}) are left to {@link ApiTokenAuthenticationMechanism}.
 */
@ApplicationScoped
public class SessionAuthenticationMechanism implements HttpAuthenticationMechanism {
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String INVALID_SESSION = "Session is invalid or has expired";

    @Inject
    SessionTokenService sessionTokenService;

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        if (context.request().getHeader(API_KEY_HEADER) != null) {
            return Uni.createFrom().nullItem();
        }

        String credential = resolveCredential(context);
        if (credential == null) {
            return Uni.createFrom().nullItem();
        }

        context.put(AuthenticationChallenge.FAILURE_DETAIL, INVALID_SESSION);
        // An invalid session leaves the request anonymous: public endpoints keep working with a stale
        // cookie, and protected ones answer 401 through the challenge.
        return identityProviderManager
                .authenticate(HttpSecurityUtils.setRoutingContextAttribute(
                        new SessionAuthenticationRequest(credential), context))
                .onFailure(AuthenticationFailedException.class).recoverWithNull();
    }

    /**
     * A bearer header wins over the cookie. Bearer values that are not session access tokens are
     * either API tokens (handled elsewhere) or retired JWTs, which are rejected.
     */
    private String resolveCredential(RoutingContext context) {
        String authorization = context.request().getHeader(AUTHORIZATION_HEADER);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            String bearer = authorization.substring(BEARER_PREFIX.length()).trim();
            if (sessionTokenService.isAccessToken(bearer)) {
                return bearer;
            }
            if (!bearer.isEmpty() && !bearer.startsWith(ApiTokenSecretService.TOKEN_PREFIX)) {
                context.put(AuthenticationChallenge.FAILURE_DETAIL, INVALID_SESSION);
            }
            return null;
        }

        Cookie cookie = context.request().getCookie(CookieService.SESSION_COOKIE);
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return null;
        }
        return cookie.getValue();
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().item(new ChallengeData(401, "WWW-Authenticate", "Bearer"));
    }

    @Override
    public Uni<Boolean> sendChallenge(RoutingContext context) {
        return AuthenticationChallenge.send(context);
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of(SessionAuthenticationRequest.class);
    }

    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        return Uni.createFrom().item(new HttpCredentialTransport(HttpCredentialTransport.Type.COOKIE,
                CookieService.SESSION_COOKIE));
    }
}
