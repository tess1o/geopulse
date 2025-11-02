package org.github.tess1o.geopulse.auth.exceptions;

public class OidcRegistrationDisabledException extends RuntimeException {
    public OidcRegistrationDisabledException(String message) {
        super(message);
    }
}
