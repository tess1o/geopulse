package org.github.tess1o.geopulse.auth.service;

/** Security identity attributes set by the GeoPulse authentication mechanisms. */
public final class IdentityAttributes {
    /** {@code "session"} or {@code "api-token"}. */
    public static final String AUTH_TYPE = "geopulse.auth.type";
    public static final String USER_ID = "geopulse.auth.userId";
    public static final String USER_EMAIL = "geopulse.auth.userEmail";
    /** Set for session-authenticated requests only. */
    public static final String SESSION_ID = "geopulse.auth.sessionId";

    public static final String AUTH_TYPE_SESSION = "session";
    public static final String AUTH_TYPE_API_TOKEN = "api-token";

    private IdentityAttributes() {
    }
}
