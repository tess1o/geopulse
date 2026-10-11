package org.github.tess1o.geopulse.auth.session.model;

public enum SessionRevocationReason {
    LOGOUT,
    /** The browser signed in again; the session it carried before is no longer needed. */
    REPLACED,
    /** The identity provider sent a back-channel logout for this session or subject. */
    IDP_LOGOUT,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
    USER_DISABLED,
    /** An already rotated refresh token was presented again: the token was probably stolen. */
    REFRESH_TOKEN_REUSE
}
