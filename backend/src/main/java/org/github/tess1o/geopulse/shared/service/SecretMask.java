package org.github.tess1o.geopulse.shared.service;

/**
 * Placeholder returned instead of a stored third-party secret (e.g. an Immich API key). Clients send it back
 * (or nothing) to keep the stored value.
 */
public final class SecretMask {

    public static final String MASK = "********";

    private SecretMask() {
    }

    public static String mask(String secret) {
        return secret == null || secret.isBlank() ? null : MASK;
    }

    /** True when the client did not provide a new secret: blank, or the mask it received. */
    public static boolean isUnchanged(String submitted) {
        return submitted == null || submitted.isBlank() || MASK.equals(submitted);
    }
}
