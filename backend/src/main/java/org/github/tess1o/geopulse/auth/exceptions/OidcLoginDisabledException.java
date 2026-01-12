package org.github.tess1o.geopulse.auth.exceptions;

public class OidcLoginDisabledException extends RuntimeException {
    public OidcLoginDisabledException(String message) {
        super(message);
    }
}
