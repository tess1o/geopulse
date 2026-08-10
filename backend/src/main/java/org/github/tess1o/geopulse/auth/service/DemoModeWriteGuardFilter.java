package org.github.tess1o.geopulse.auth.service;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

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
        String path = normalizePath(requestContext.getUriInfo().getPath());

        if (isBlocked(method, path, securityIdentity)) {
            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                    .type(MediaType.APPLICATION_JSON)
                    .header(DEMO_BLOCK_HEADER, "true")
                    .entity(ApiResponse.error("This action is disabled in demo mode."))
                    .build());
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

        return path.equals("api/users/register")
                || path.equals("api/auth/invitation/register")
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
                && (path.equals("api/gps/export")
                || path.equals("api/export")
                || path.startsWith("api/export/")
                || path.matches("api/location-analytics/.+/visits/export")
                || path.matches("api/place-details/.+/visits/export"));
    }

    private boolean isAllowedDemoWrite(String method, String path) {
        if (!"POST".equals(method)) {
            return false;
        }

        return path.equals("api/auth/login")
                || path.equals("api/auth/api-login")
                || path.equals("api/auth/demo-login")
                || path.equals("api/auth/refresh")
                || path.equals("api/auth/refresh-cookie")
                || path.equals("api/auth/logout")
                || path.startsWith("api/auth/oidc/login/")
                || path.equals("api/auth/oidc/callback")
                || path.matches("api/shared/[^/]+/verify");
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
