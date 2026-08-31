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

@Provider
@Priority(Priorities.AUTHORIZATION)
public class BackupRestoreGuardFilter implements ContainerRequestFilter {
    private static final String RESTORE_BLOCK_HEADER = "X-GeoPulse-Restore-Blocked";
    private static final String RESTORE_BLOCK_MESSAGE =
            "GeoPulse full restore did not complete. Retry a full restore before using the app.";

    @Inject
    BackupMaintenanceService backupMaintenanceService;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!backupMaintenanceService.isRestoreBlocked()) {
            return;
        }

        String method = requestContext.getMethod().toUpperCase(Locale.ROOT);
        String path = normalizePath(requestContext.getUriInfo().getPath());
        if (!path.startsWith("api/") || isAllowedWhileRestoreBlocked(method, path)) {
            return;
        }

        requestContext.abortWith(Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .type(MediaType.APPLICATION_JSON)
                .header(RESTORE_BLOCK_HEADER, "true")
                .entity(ApiResponse.error(RESTORE_BLOCK_MESSAGE))
                .build());
    }

    private boolean isAllowedWhileRestoreBlocked(String method, String path) {
        if ("OPTIONS".equals(method)) {
            return true;
        }

        if ("GET".equals(method)
                && (path.equals("api/health")
                || path.equals("api/version")
                || path.equals("api/admin/backups/status")
                || path.equals("api/admin/backups/files")
                || path.startsWith("api/admin/backups/files/"))) {
            return true;
        }

        return "POST".equals(method)
                && (path.equals("api/admin/backups/restore/upload")
                || path.equals("api/admin/backups/restore/local"));
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
