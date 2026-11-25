package org.github.tess1o.geopulse.auth.oidc.http;

/** Raised when a request to an OIDC provider fails (TLS, I/O, non-2xx, bad payload). */
public class OidcHttpException extends RuntimeException {
    public OidcHttpException(String message) {
        super(message);
    }

    public OidcHttpException(String message, Throwable cause) {
        super(message, cause);
    }
}
