package org.github.tess1o.geopulse.auth.exceptions;

public class OIDCAuthFailedException extends RuntimeException {
    public OIDCAuthFailedException(String s, Exception e) {
        super(s, e);
    }
}
