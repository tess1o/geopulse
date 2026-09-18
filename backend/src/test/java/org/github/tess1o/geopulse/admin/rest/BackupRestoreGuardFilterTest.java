package org.github.tess1o.geopulse.admin.rest;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.github.tess1o.geopulse.admin.service.BackupMaintenanceService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class BackupRestoreGuardFilterTest {
    @Test
    void blocksApplicationRequestsWithConsistentMaintenanceResponse() {
        BackupRestoreGuardFilter filter = filter(true);
        ContainerRequestContext context = request("POST", "api/v1/users");

        filter.filter(context);

        ArgumentCaptor<Response> response = ArgumentCaptor.forClass(Response.class);
        verify(context).abortWith(response.capture());
        assertThat(response.getValue().getStatus()).isEqualTo(503);
        assertThat(response.getValue().getHeaderString(BackupRestoreGuardFilter.RESTORE_BLOCK_HEADER)).isEqualTo("true");
        assertThat(response.getValue().getHeaderString("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void allowsOnlyPublicStatusAndRecoveryEndpointsWhileBlocked() {
        BackupRestoreGuardFilter filter = filter(true);
        ContainerRequestContext maintenance = request("GET", "/api/v1/system/maintenance/");
        ContainerRequestContext retry = request("POST", "api/v1/admin/backups/restore/retry");
        ContainerRequestContext logout = request("DELETE", "api/v1/auth/sessions/current");
        ContainerRequestContext protectedAdmin = request("GET", "api/v1/admin/users");

        filter.filter(maintenance);
        filter.filter(retry);
        filter.filter(logout);
        filter.filter(protectedAdmin);

        verify(maintenance, never()).abortWith(any());
        verify(retry, never()).abortWith(any());
        verify(logout, never()).abortWith(any());
        verify(protectedAdmin).abortWith(any());
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
