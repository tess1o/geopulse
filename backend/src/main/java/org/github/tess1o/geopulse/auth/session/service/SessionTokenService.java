package org.github.tess1o.geopulse.auth.session.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.shared.security.CryptoUtils;


/**
 * Opaque session credentials: 256 random bits behind a type prefix. The prefix lets the
 * authentication mechanisms tell session credentials apart from API tokens ({@code gp_sa_}).
 */
@ApplicationScoped
public class SessionTokenService {
    public static final String BROWSER_SESSION_PREFIX = "gp_bs_";
    public static final String ACCESS_TOKEN_PREFIX = "gp_at_";
    public static final String REFRESH_TOKEN_PREFIX = "gp_rt_";

    private static final int RANDOM_BYTES = 32;

    public String generate(String prefix) {
        return prefix + CryptoUtils.randomUrlSafeToken(RANDOM_BYTES);
    }

    public String hash(String rawToken) {
        return CryptoUtils.sha256Hex(rawToken);
    }

    public boolean isAccessToken(String token) {
        return token != null && token.startsWith(ACCESS_TOKEN_PREFIX);
    }

    public boolean isBrowserSession(String token) {
        return token != null && token.startsWith(BROWSER_SESSION_PREFIX);
    }
}
