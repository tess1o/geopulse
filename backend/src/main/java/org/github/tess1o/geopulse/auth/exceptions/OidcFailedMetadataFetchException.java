package org.github.tess1o.geopulse.auth.exceptions;

public class OidcFailedMetadataFetchException extends RuntimeException {
    public OidcFailedMetadataFetchException(String s, Exception e) {
        super(s, e);
    }
}
