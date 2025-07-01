package org.github.tess1o.geopulse.auth.exceptions;

public class InvalidPasswordException extends RuntimeException {
    public InvalidPasswordException(Throwable cause) {
        super(cause);
    }
    public InvalidPasswordException(String message) {
        super(message);
    }
}
