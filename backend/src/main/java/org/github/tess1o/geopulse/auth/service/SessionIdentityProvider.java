package org.github.tess1o.geopulse.auth.service;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.auth.session.model.AuthenticatedSession;
import org.github.tess1o.geopulse.auth.session.service.UserSessionService;

import java.security.Principal;

/**
 * Turns a session credential into a security identity. Going through an identity provider (rather
 * than building the identity in the mechanism) lets the security identity augmentors, such as the
 * demo-mode roles, apply to session users.
 */
@ApplicationScoped
public class SessionIdentityProvider implements IdentityProvider<SessionAuthenticationRequest> {

    @Inject
    UserSessionService userSessionService;

    @Override
    public Class<SessionAuthenticationRequest> getRequestType() {
        return SessionAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(SessionAuthenticationRequest request, AuthenticationRequestContext context) {
        return context.runBlocking(() -> userSessionService.authenticate(request.getToken())
                .map(SessionIdentityProvider::toIdentity)
                .orElseThrow(AuthenticationFailedException::new));
    }

    private static SecurityIdentity toIdentity(AuthenticatedSession session) {
        Principal principal = session::email;
        return QuarkusSecurityIdentity.builder()
                .setPrincipal(principal)
                .addRole(session.role())
                .addAttribute(IdentityAttributes.AUTH_TYPE, IdentityAttributes.AUTH_TYPE_SESSION)
                .addAttribute(IdentityAttributes.USER_ID, session.userId().toString())
                .addAttribute(IdentityAttributes.USER_EMAIL, session.email())
                .addAttribute(IdentityAttributes.SESSION_ID, session.sessionId().toString())
                .build();
    }
}
