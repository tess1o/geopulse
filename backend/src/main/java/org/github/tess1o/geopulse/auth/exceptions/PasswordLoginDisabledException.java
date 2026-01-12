package org.github.tess1o.geopulse.auth.exceptions;

public class PasswordLoginDisabledException extends RuntimeException {
    public PasswordLoginDisabledException(String message) {
        super(message);
    }
}
