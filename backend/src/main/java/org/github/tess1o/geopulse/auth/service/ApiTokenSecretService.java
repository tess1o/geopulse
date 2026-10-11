package org.github.tess1o.geopulse.auth.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.shared.security.CryptoUtils;


@ApplicationScoped
public class ApiTokenSecretService {
    public static final String TOKEN_PREFIX = "gp_sa_";

    private static final int RANDOM_BYTES = 48;
    private static final int TOKEN_SUFFIX_LENGTH = 8;

    public String generateToken() {
        return TOKEN_PREFIX + CryptoUtils.randomUrlSafeToken(RANDOM_BYTES);
    }

    public String hashToken(String rawToken) {
        return CryptoUtils.sha256Hex(rawToken);
    }

    public boolean hasTokenPrefix(String rawToken) {
        return rawToken != null && rawToken.startsWith(TOKEN_PREFIX);
    }

    public String suffix(String rawToken) {
        return rawToken.substring(rawToken.length() - TOKEN_SUFFIX_LENGTH);
    }
}
