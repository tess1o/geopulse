package org.github.tess1o.geopulse.auth.service;

import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.NewCookie;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

@ApplicationScoped
public class CookieService {

    @Inject
    @ConfigProperty(name = "geopulse.auth.secure-cookies", defaultValue = "false")
    @StaticInitSafe
    boolean secureCookies;

    @Inject
    @ConfigProperty(name = "geopulse.auth.cookie-domain")
    @StaticInitSafe
    Optional<String> cookieDomain;

    public static final String SESSION_COOKIE = "gp_session";
    static final String CSRF_COOKIE = "csrf-token";
    /** Cookies of the JWT-based sessions used before GeoPulse 2.0, cleared on logout. */
    private static final String[] LEGACY_COOKIES = {"access_token", "refresh_token", "token_expires_at"};
    private static final int CSRF_COOKIE_MAX_AGE_SECONDS = 2 * 60 * 60;

    /**
     * Helper method to create cookie with common settings
     */
    private NewCookie createCookie(String name, String value, int maxAge, boolean httpOnly) {
        Optional<String> normalizedCookieDomain = getNormalizedCookieDomain();

        if (normalizedCookieDomain.isPresent()) {
            // Create cookie with domain for cross-subdomain sharing
            return new NewCookie.Builder(name)
                    .value(value)
                    .httpOnly(httpOnly)
                    .secure(secureCookies)
                    .sameSite(NewCookie.SameSite.LAX) // LAX allows cross-subdomain requests
                    .maxAge(maxAge)
                    .path("/")
                    .domain(normalizedCookieDomain.get())
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

    private Optional<String> getNormalizedCookieDomain() {
        return cookieDomain
                .map(String::trim)
                .filter(domain -> !domain.isEmpty())
                .filter(domain -> !domain.equals("\"\""));
    }

    /**
     * Create the secure httpOnly cookie carrying the opaque browser session secret.
     * The server decides when the session ends; the cookie only needs to outlive it.
     */
    public NewCookie createSessionCookie(String sessionSecret, long maxAgeSeconds) {
        return createCookie(SESSION_COOKIE, sessionSecret, (int) Math.min(maxAgeSeconds, Integer.MAX_VALUE), true);
    }

    /**
     * Create non-httpOnly cookie with the CSRF token, which the frontend echoes in a request header
     */
    public NewCookie createCsrfCookie(String csrfToken) {
        return createCookie(CSRF_COOKIE, csrfToken, CSRF_COOKIE_MAX_AGE_SECONDS, false);
    }

    /**
     * Create cookies for clearing tokens on logout
     */
    public NewCookie[] createLogoutCookies() {
        NewCookie[] cookies = new NewCookie[LEGACY_COOKIES.length + 2];
        cookies[0] = createCookie(SESSION_COOKIE, "", 0, true);
        cookies[1] = createCookie(CSRF_COOKIE, "", 0, false);
        for (int i = 0; i < LEGACY_COOKIES.length; i++) {
            cookies[i + 2] = createCookie(LEGACY_COOKIES[i], "", 0, true);
        }
        return cookies;
    }

}
