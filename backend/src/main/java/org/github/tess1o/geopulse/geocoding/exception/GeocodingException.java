package org.github.tess1o.geopulse.geocoding.exception;

/**
 * Base exception for geocoding-related errors.
 */
public class GeocodingException extends RuntimeException {
    
    public GeocodingException(String message) {
        super(message);
    }
    
    public GeocodingException(String message, Throwable cause) {
        super(message, cause);
    }
}