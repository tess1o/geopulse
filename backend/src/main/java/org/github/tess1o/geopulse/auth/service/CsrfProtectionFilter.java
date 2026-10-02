package org.github.tess1o.geopulse.auth.service;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.CSRF_TOKEN_INVALID;

/**
 * Double-submit-cookie CSRF protection for requests authenticated by a session cookie.
 *
 * <p>Safe requests without a CSRF cookie are issued one. Unsafe requests that carry a session cookie
 * must echo the CSRF cookie in the {@value #CSRF_HEADER} header: always for form and multipart bodies,
 * and whenever the header is present for other bodies. Requests without a session cookie — API tokens
 * and bearer tokens sent in a header — have no ambient credential to abuse and are not checked.</p>
 *
 * <p>The check keys on the presence of a session cookie rather than on which mechanism authenticated
 * the request, so adding an API key header to a cross-site request does not bypass it.</p>
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class CsrfProtectionFilter implements ContainerRequestFilter, ContainerResponseFilter {
    public static final String CSRF_HEADER = "X-CSRF-Token";

    private static final String NEW_TOKEN_PROPERTY = "geopulse.csrf.newToken";
    private static final int TOKEN_SIZE_BYTES = 16;

    @Inject
    CookieService cookieService;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        Map<String, Cookie> cookies = requestContext.getCookies();
        Cookie csrfCookie = cookies.get(CookieService.CSRF_COOKIE);

        if (isSafeMethod(requestContext.getMethod())) {
            if (csrfCookie == null) {
                requestContext.setProperty(NEW_TOKEN_PROPERTY, newToken());
            }
            return;
        }

        if (!hasSessionCookie(cookies)) {
            return;
        }

        String headerToken = requestContext.getHeaderString(CSRF_HEADER);
        if (headerToken == null || headerToken.isBlank()) {
            if (isFormRequest(requestContext.getMediaType())) {
                throw new GeoPulseException(CSRF_TOKEN_INVALID, "CSRF token is required");
            }
            return;
        }

        if (csrfCookie == null || !constantTimeEquals(csrfCookie.getValue(), headerToken)) {
            throw new GeoPulseException(CSRF_TOKEN_INVALID, "CSRF token is invalid");
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Object newToken = requestContext.getProperty(NEW_TOKEN_PROPERTY);
        if (newToken != null) {
            responseContext.getHeaders().add(HttpHeaders.SET_COOKIE, cookieService.createCsrfCookie((String) newToken));
        }
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_SIZE_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean isSafeMethod(String method) {
        String normalized = method.toUpperCase(Locale.ROOT);
        return "GET".equals(normalized) || "HEAD".equals(normalized) || "OPTIONS".equals(normalized);
    }

    private boolean hasSessionCookie(Map<String, Cookie> cookies) {
        return cookies.containsKey(CookieService.ACCESS_TOKEN_COOKIE)
                || cookies.containsKey(CookieService.REFRESH_TOKEN_COOKIE);
    }

    private boolean isFormRequest(MediaType mediaType) {
        return mediaType != null
                && (mediaType.isCompatible(MediaType.MULTIPART_FORM_DATA_TYPE)
                || mediaType.isCompatible(MediaType.APPLICATION_FORM_URLENCODED_TYPE));
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
