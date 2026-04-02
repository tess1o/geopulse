package org.github.tess1o.geopulse.auth.service;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * Request/response logging for auth endpoints.
 * Helps diagnose CSRF/CORS/security-layer failures where response body may be empty.
 */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION - 100)
@Slf4j
public class AuthRequestLoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String PROP_TRACK = "auth.log.track";
    private static final String PROP_CSRF_COOKIE = "auth.log.csrfCookie";
    private static final String PROP_CSRF_HEADER = "auth.log.csrfHeader";
    private static final String PROP_CSRF_MATCH = "auth.log.csrfMatch";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = normalizePath(requestContext.getUriInfo().getPath());
        if (!isTrackedAuthPath(path)) {
            return;
        }

        boolean csrfCookiePresent = hasCookie(requestContext, "csrf-token");
        boolean csrfHeaderPresent = hasHeader(requestContext, "X-CSRF-Token");
        boolean csrfHeaderMatchesCookie = csrfCookiePresent
                && csrfHeaderPresent
                && csrfTokenMatches(requestContext, "csrf-token", "X-CSRF-Token");
        requestContext.setProperty(PROP_TRACK, true);
        requestContext.setProperty(PROP_CSRF_COOKIE, csrfCookiePresent);
        requestContext.setProperty(PROP_CSRF_HEADER, csrfHeaderPresent);
        requestContext.setProperty(PROP_CSRF_MATCH, csrfHeaderMatchesCookie);

        // CSRF protection may reject before resource code runs and can return empty-body 400/403.
        // Emit early warning when request is likely to be blocked.
        if (isStateChangingMethod(requestContext.getMethod()) && csrfCookiePresent && !csrfHeaderPresent) {
            log.warn(
                    "Auth request likely to fail CSRF validation: method={} path=/{} csrfCookiePresent=true csrfHeaderPresent=false origin={} referer={}",
                    requestContext.getMethod(),
                    path,
                    safe(requestContext.getHeaderString("Origin")),
                    safe(requestContext.getHeaderString("Referer"))
            );
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        if (!Boolean.TRUE.equals(requestContext.getProperty(PROP_TRACK))) {
            return;
        }

        int status = responseContext.getStatus();
        if (status < 400) {
            return;
        }

        String path = "/" + normalizePath(requestContext.getUriInfo().getPath());
        String method = requestContext.getMethod();
        String origin = safe(requestContext.getHeaderString("Origin"));
        String referer = safe(requestContext.getHeaderString("Referer"));
        String userAgent = safe(requestContext.getHeaderString("User-Agent"));
        String contentType = safe(requestContext.getHeaderString("Content-Type"));
        String responseType = responseContext.getMediaType() != null ? responseContext.getMediaType().toString() : "<none>";
        Object csrfCookie = requestContext.getProperty(PROP_CSRF_COOKIE);
        Object csrfHeader = requestContext.getProperty(PROP_CSRF_HEADER);
        Object csrfMatch = requestContext.getProperty(PROP_CSRF_MATCH);
        String suspectedCause = detectSuspectedCause(status, csrfCookie, csrfHeader, csrfMatch);

        log.warn(
                "Auth request failed: method={} path={} status={} suspectedCause={} csrfCookiePresent={} csrfHeaderPresent={} csrfHeaderMatchesCookie={} origin={} referer={} requestContentType={} responseContentType={} userAgent={}",
                method,
                path,
                status,
                suspectedCause,
                csrfCookie,
                csrfHeader,
                csrfMatch,
                origin,
                referer,
                contentType,
                responseType,
                userAgent
        );
    }

    private static boolean isTrackedAuthPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }

        return path.equals("api/auth/login")
                || path.equals("api/auth/logout")
                || path.equals("api/auth/refresh-cookie")
                || path.equals("api/auth/api-login")
                || path.equals("auth/login")
                || path.equals("auth/logout")
                || path.equals("auth/refresh-cookie")
                || path.equals("auth/api-login");
    }

    private static boolean isStateChangingMethod(String method) {
        if (method == null) {
            return false;
        }
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }

        String normalized = path;
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static boolean hasCookie(ContainerRequestContext requestContext, String name) {
        Cookie cookie = requestContext.getCookies().get(name);
        return cookie != null && cookie.getValue() != null && !cookie.getValue().isBlank();
    }

    private static boolean hasHeader(ContainerRequestContext requestContext, String name) {
        String header = requestContext.getHeaderString(name);
        return header != null && !header.isBlank();
    }

    private static boolean csrfTokenMatches(ContainerRequestContext requestContext, String cookieName, String headerName) {
        Cookie cookie = requestContext.getCookies().get(cookieName);
        String header = requestContext.getHeaderString(headerName);
        if (cookie == null || cookie.getValue() == null || header == null) {
            return false;
        }
        return cookie.getValue().equals(header.trim());
    }

    private static String detectSuspectedCause(int status, Object csrfCookie, Object csrfHeader, Object csrfMatch) {
        if (status != 400 && status != 403) {
            return "application-auth-or-validation";
        }

        boolean cookiePresent = Boolean.TRUE.equals(csrfCookie);
        boolean headerPresent = Boolean.TRUE.equals(csrfHeader);
        boolean headerMatches = Boolean.TRUE.equals(csrfMatch);

        if (cookiePresent && !headerPresent) {
            return "csrf-missing-header";
        }
        if (cookiePresent && headerPresent && !headerMatches) {
            return "csrf-token-mismatch";
        }
        if (cookiePresent && headerPresent && headerMatches) {
            return "csrf-passed-or-other-security-layer";
        }
        return "security-layer-unknown";
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "<none>" : value;
    }
}
