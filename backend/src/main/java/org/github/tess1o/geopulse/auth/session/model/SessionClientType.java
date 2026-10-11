package org.github.tess1o.geopulse.auth.session.model;

public enum SessionClientType {
    /** Web app: an opaque HttpOnly session cookie, no refresh token. */
    BROWSER,
    /** API sessions and the mobile app: a bearer access token plus a rotating refresh token. */
    API
}
