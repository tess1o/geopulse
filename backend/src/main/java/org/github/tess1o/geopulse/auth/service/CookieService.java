package org.github.tess1o.geopulse.auth.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.NewCookie;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

@ApplicationScoped
public class CookieService {

    @Inject
    @ConfigProperty(name = "geopulse.auth.secure-cookies", defaultValue = "false")
    boolean secureCookies;

    @Inject
    @ConfigProperty(name = "geopulse.auth.cookie-domain")
    Optional<String> cookieDomain;


    @Inject
    @ConfigProperty(name = "quarkus.rest-csrf.cookie-name", defaultValue = "csrf-token")
    String csrfCookieName;

    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final String TOKEN_EXPIRATION_COOKIE = "token_expires_at";

    /**
     * Helper method to create cookie with common settings
     */
    private NewCookie createCookie(String name, String value, int maxAge, boolean httpOnly) {
        if (cookieDomain.isPresent() && !cookieDomain.get().trim().isEmpty()) {
            // Create cookie with domain for cross-subdomain sharing
            return new NewCookie.Builder(name)
                    .value(value)
                    .httpOnly(httpOnly)
                    .secure(secureCookies)
                    .sameSite(NewCookie.SameSite.LAX) // LAX allows cross-subdomain requests
                    .maxAge(maxAge)
                    .path("/")
                    .domain(cookieDomain.get())
                    .build();
        } else {
            // Create cookie without domain
            return new NewCookie.Builder(name)
                    .value(value)
                    .httpOnly(httpOnly)
                    .secure(secureCookies)
                    .sameSite(NewCookie.SameSite.LAX)
                    .maxAge(maxAge)
                    .path("/")
                    .build();
        }
    }

    /**
     * Create secure httpOnly cookie for access token
     */
    public NewCookie createAccessTokenCookie(String accessToken, long expiresInSeconds) {
        return createCookie(ACCESS_TOKEN_COOKIE, accessToken, (int) expiresInSeconds, true);
    }

    /**
     * Create secure httpOnly cookie for refresh token
     */
    public NewCookie createRefreshTokenCookie(String refreshToken, long expiresInSeconds) {
        return createCookie(REFRESH_TOKEN_COOKIE, refreshToken, (int) expiresInSeconds, true);
    }

    /**
     * Create non-httpOnly cookie with token expiration timestamp for frontend
     */
    public NewCookie createTokenExpirationCookie(long expiresInSeconds) {
        long expirationTime = System.currentTimeMillis() + (expiresInSeconds * 1000);
        return createCookie(TOKEN_EXPIRATION_COOKIE, String.valueOf(expirationTime), (int) expiresInSeconds, false);
    }


    /**
     * Create cookies for clearing tokens on logout
     */
    public NewCookie[] createLogoutCookies() {
        return new NewCookie[]{
                createCookie(ACCESS_TOKEN_COOKIE, "", 0, true),
                createCookie(REFRESH_TOKEN_COOKIE, "", 0, true),
                createCookie(TOKEN_EXPIRATION_COOKIE, "", 0, false),
                createCookie(csrfCookieName, "", 0, false)
        };
    }

}