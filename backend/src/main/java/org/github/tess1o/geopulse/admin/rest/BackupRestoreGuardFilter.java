package org.github.tess1o.geopulse.admin.rest;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.github.tess1o.geopulse.admin.service.BackupMaintenanceService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.util.Locale;
import java.util.Set;

@Provider
@Priority(Priorities.AUTHORIZATION)
public class BackupRestoreGuardFilter implements ContainerRequestFilter {
    public static final String RESTORE_BLOCK_HEADER = "X-GeoPulse-Restore-Blocked";
    private static final String RESTORE_BLOCK_MESSAGE =
            "GeoPulse is unavailable while full restore activation requires administrator attention.";
    private static final Set<String> PUBLIC_GET_PATHS = Set.of(
            "api/maintenance/status", "api/health", "api/version", "api/version/status");
    private static final String ADMIN_STATUS_PATH = "api/admin/backups/status";
    private static final Set<String> RECOVERY_POST_PATHS = Set.of(
            "api/admin/backups/restore/retry", "api/admin/backups/restore/discard", "api/auth/logout");

    @Inject BackupMaintenanceService maintenanceService;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!maintenanceService.isRestoreBlocked()) return;
        String method = requestContext.getMethod().toUpperCase(Locale.ROOT);
        String path = normalizePath(requestContext.getUriInfo().getPath());
        if (!path.startsWith("api/") || "OPTIONS".equals(method)
                || ("GET".equals(method) && PUBLIC_GET_PATHS.contains(path))
                || ("GET".equals(method) && ADMIN_STATUS_PATH.equals(path))
                || ("POST".equals(method) && RECOVERY_POST_PATHS.contains(path))) {
            return;
        }
        requestContext.abortWith(Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .type(MediaType.APPLICATION_JSON)
                .header(RESTORE_BLOCK_HEADER, "true")
                .header("Cache-Control", "no-store")
                .entity(ApiResponse.error(RESTORE_BLOCK_MESSAGE))
                .build());
    }

    private String normalizePath(String path) {
        if (path == null) return "";
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
