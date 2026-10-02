package org.github.tess1o.geopulse.auth.service;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import io.quarkiverse.httpproblem.HttpProblem;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.ACCESS_DENIED;

import java.util.Locale;

@Provider
@Priority(Priorities.AUTHORIZATION)
public class DemoModeWriteGuardFilter implements ContainerRequestFilter {
    private static final String DEMO_BLOCK_HEADER = "X-GeoPulse-Demo-Blocked";

    @Inject
    DemoModeService demoModeService;

    @Inject
    SecurityIdentity securityIdentity;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!demoModeService.isEnabled()) {
            return;
        }

        String method = requestContext.getMethod().toUpperCase(Locale.ROOT);
        String path = normalizePath(requestContext.getUriInfo().getRequestUri().getPath());

        if (isBlocked(method, path, securityIdentity)) {
            throw HttpProblem.builder()
                    .withStatus(ACCESS_DENIED.statusCode())
                    .withTitle(ACCESS_DENIED.title())
                    .withDetail("This action is disabled in demo mode.")
                    .with("code", ACCESS_DENIED)
                    .withHeader(DEMO_BLOCK_HEADER, "true")
                    .build();
        }
    }

    private boolean isBlocked(String method, String path, SecurityIdentity identity) {
        if (isPublicDemoWrite(method, path)) {
            return true;
        }

        if (isExportRead(method, path)) {
            return true;
        }

        if (!demoModeService.isDemoRestricted(identity)) {
            return false;
        }

        if (isUnsafeMethod(method) && !isAllowedDemoWrite(method, path)) {
            return true;
        }

        return false;
    }

    private boolean isUnsafeMethod(String method) {
        return "POST".equals(method)
                || "PUT".equals(method)
                || "PATCH".equals(method)
                || "DELETE".equals(method);
    }

    private boolean isPublicDemoWrite(String method, String path) {
        if (!"POST".equals(method)) {
            return false;
        }

        return path.equals("api/v1/registrations")
                || path.matches("api/v1/registration-invitations/[^/]+/registrations")
                || path.equals("api/v1/gps/ingest/owntracks")
                || path.equals("api/v1/gps/ingest/overland")
                || path.equals("api/v1/gps/ingest/traccar")
                || path.equals("api/v1/gps/ingest/gpslogger")
                || path.equals("api/v1/gps/ingest/home-assistant")
                || path.equals("api/v1/gps/ingest/colota")
                || path.equals("api/v1/gps/ingest/dawarich/points")
                || path.equals("api/owntracks")
                || path.equals("api/overland")
                || path.equals("api/traccar")
                || path.equals("api/gpslogger")
                || path.equals("api/homeassistant")
                || path.equals("api/colota")
                || path.equals("api/dawarich/api/v1/points");
    }

    private boolean isExportRead(String method, String path) {
        return "GET".equals(method)
                && (path.equals("api/v1/exports")
                || path.startsWith("api/v1/exports/")
                || path.equals("api/v1/gps/points/exports")
                || path.matches("api/v1/location-analytics/.+/visits/export")
                || path.matches("api/v1/places/.+/visits/export"));
    }

    private boolean isAllowedDemoWrite(String method, String path) {
        if ("POST".equals(method)) {
            return path.equals("api/v1/auth/sessions")
                    || path.equals("api/v1/auth/api-sessions")
                    || path.equals("api/v1/auth/demo-sessions")
                    || path.equals("api/v1/auth/sessions/current/refresh")
                    || path.equals("api/v1/auth/api-sessions/current/refresh")
                    || path.matches("api/v1/auth/oidc/login-authorizations/[^/]+")
                    || path.equals("api/v1/auth/oidc/callbacks")
                    || path.matches("api/v1/public/share-links/[^/]+/access-tokens");
        }

        if ("DELETE".equals(method)) {
            return path.equals("api/v1/auth/sessions/current");
        }

        return false;
    }

    private String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
