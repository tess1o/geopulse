package org.github.tess1o.geopulse.sharing.exceptions;

/**
 * Exception thrown when a user tries to create more shared links than allowed.
 */
public class TooManyLinksException extends RuntimeException {
    
    public TooManyLinksException(String message) {
        super(message);
    }
    
}