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
import io.quarkiverse.httpproblem.HttpProblem;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.RESTORE_IN_PROGRESS;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

import java.util.Locale;
import java.util.Set;

@Provider
@Priority(Priorities.AUTHORIZATION)
public class BackupRestoreGuardFilter implements ContainerRequestFilter {
    public static final String RESTORE_BLOCK_HEADER = "X-GeoPulse-Restore-Blocked";
    private static final String RESTORE_BLOCK_MESSAGE =
            "GeoPulse is unavailable while full restore activation requires administrator attention.";
    private static final Set<String> PUBLIC_GET_PATHS = Set.of(
            "api/v1/system/maintenance", "api/v1/system/health",
            "api/v1/system/version", "api/v1/system/version/status");
    private static final String ADMIN_STATUS_PATH = "api/v1/admin/backups/status";
    private static final Set<String> RECOVERY_POST_PATHS = Set.of(
            "api/v1/admin/backups/restore/retry", "api/v1/admin/backups/restore/discard");
    private static final String LOGOUT_PATH = "api/v1/auth/sessions/current";

    @Inject BackupMaintenanceService maintenanceService;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!maintenanceService.isRestoreBlocked()) return;
        String method = requestContext.getMethod().toUpperCase(Locale.ROOT);
        String path = normalizePath(requestContext.getUriInfo().getRequestUri().getPath());
        if (!path.startsWith("api/") || "OPTIONS".equals(method)
                || ("GET".equals(method) && PUBLIC_GET_PATHS.contains(path))
                || ("GET".equals(method) && ADMIN_STATUS_PATH.equals(path))
                || ("POST".equals(method) && RECOVERY_POST_PATHS.contains(path))
                || ("DELETE".equals(method) && LOGOUT_PATH.equals(path))) {
            return;
        }
        HttpProblem problem = problem(RESTORE_IN_PROGRESS, RESTORE_BLOCK_MESSAGE);
        requestContext.abortWith(Response.fromResponse(problem.toResponse())
                .header(RESTORE_BLOCK_HEADER, "true")
                .header("Cache-Control", "no-store")
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
