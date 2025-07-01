package org.github.tess1o.geopulse.user.exceptions;

public class NotAuthorizedUserException extends RuntimeException {

    public NotAuthorizedUserException(Throwable cause) {
        super(cause);
    }

    public NotAuthorizedUserException(String message) {
        super(message);
    }
}
