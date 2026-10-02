package org.github.tess1o.geopulse.admin.rest;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriInfo;
import org.github.tess1o.geopulse.admin.service.BackupMaintenanceService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@Tag("unit")
class BackupRestoreGuardFilterTest {
    @Test
    void blocksApplicationRequestsWithConsistentMaintenanceResponse() {
        BackupRestoreGuardFilter filter = filter(true);
        ContainerRequestContext context = request("POST", "api/v1/users");

        HttpProblem problem = assertThrows(HttpProblem.class, () -> filter.filter(context));

        assertThat(problem.getStatusCode()).isEqualTo(503);
        assertThat(problem.getHeaders()).containsEntry(BackupRestoreGuardFilter.RESTORE_BLOCK_HEADER, "true");
        assertThat(problem.getHeaders()).containsEntry("Cache-Control", "no-store");
        verify(context, never()).abortWith(any());
    }

    @Test
    void allowsOnlyPublicStatusAndRecoveryEndpointsWhileBlocked() {
        BackupRestoreGuardFilter filter = filter(true);
        ContainerRequestContext maintenance = request("GET", "/api/v1/system/maintenance/");
        ContainerRequestContext retry = request("POST", "api/v1/admin/backups/restore/retry");
        ContainerRequestContext logout = request("DELETE", "api/v1/auth/sessions/current");
        ContainerRequestContext protectedAdmin = request("GET", "api/v1/admin/users");

        assertDoesNotThrow(() -> filter.filter(maintenance));
        assertDoesNotThrow(() -> filter.filter(retry));
        assertDoesNotThrow(() -> filter.filter(logout));
        assertThrows(HttpProblem.class, () -> filter.filter(protectedAdmin));

        verify(maintenance, never()).abortWith(any());
        verify(retry, never()).abortWith(any());
        verify(logout, never()).abortWith(any());
        verify(protectedAdmin, never()).abortWith(any());
    }

    @Test
    void doesNotInterfereOutsideBlockedRestoreState() {
        BackupRestoreGuardFilter filter = filter(false);
        ContainerRequestContext context = request("POST", "api/v1/users");
        filter.filter(context);
        verify(context, never()).abortWith(any());
    }

    private BackupRestoreGuardFilter filter(boolean blocked) {
        BackupMaintenanceService maintenanceService = mock(BackupMaintenanceService.class);
        when(maintenanceService.isRestoreBlocked()).thenReturn(blocked);
        BackupRestoreGuardFilter filter = new BackupRestoreGuardFilter();
        filter.maintenanceService = maintenanceService;
        return filter;
    }

    private ContainerRequestContext request(String method, String path) {
        ContainerRequestContext context = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        String uriPath = path.startsWith("/") ? path : "/" + path;
        when(context.getMethod()).thenReturn(method);
        when(context.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://localhost" + uriPath));
        return context;
    }
}
