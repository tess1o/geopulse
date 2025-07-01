package org.github.tess1o.geopulse.geocoding.exception;

/**
 * Exception thrown when geocoding cache operations fail.
 */
public class GeocodingCacheException extends GeocodingException {

    public GeocodingCacheException(String message, Throwable cause) {
        super("Geocoding cache error: " + message, cause);
    }
}