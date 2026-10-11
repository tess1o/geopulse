package org.github.tess1o.geopulse.auth.session.model;

/**
 * How a session was created, recorded on the session row.
 * The OIDC fields are only set for {@link SessionAuthMethod#OIDC} sign-ins.
 */
public record SessionContext(SessionAuthMethod authMethod,
                             String oidcProvider,
                             String idpSubject,
                             String idpSessionId,
                             String idpIdToken,
                             String userAgent,
                             String ipAddress) {

    public static SessionContext of(SessionAuthMethod authMethod, String userAgent, String ipAddress) {
        return new SessionContext(authMethod, null, null, null, null, userAgent, ipAddress);
    }

    public static SessionContext oidc(String provider, String subject, String sessionId, String idToken,
                                      String userAgent, String ipAddress) {
        return new SessionContext(SessionAuthMethod.OIDC, provider, subject, sessionId, idToken, userAgent, ipAddress);
    }
}
