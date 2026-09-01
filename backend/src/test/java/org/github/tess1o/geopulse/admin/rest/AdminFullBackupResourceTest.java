package org.github.tess1o.geopulse.admin.rest;

import io.vertx.core.http.HttpServerRequest;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.service.AdminFullBackupScheduler;
import org.github.tess1o.geopulse.admin.service.AdminFullBackupService;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.admin.service.BackupMaintenanceService;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class AdminFullBackupResourceTest {
    @TempDir Path directory;
    @Mock AdminFullBackupService backupService;
    @Mock BackupMaintenanceService maintenanceService;
    @Mock AdminFullBackupScheduler scheduler;
    @Mock CurrentUserService currentUserService;
    @Mock AuditLogService auditLogService;
    @Mock HttpServerRequest httpRequest;
    AdminFullBackupResource resource;

    @BeforeEach
    void setUp() {
        resource = new AdminFullBackupResource();
        resource.backupService = backupService;
        resource.maintenanceService = maintenanceService;
        resource.backupScheduler = scheduler;
        resource.currentUserService = currentUserService;
        resource.auditLogService = auditLogService;
        resource.httpRequest = httpRequest;
        lenient().when(currentUserService.getCurrentUserId()).thenReturn(UUID.randomUUID());
    }

    @Test
    void marksPublishedBackupCompleteBeforeBrowserStreamingAndKeepsDisconnectSeparate() throws Exception {
        String fileName = "geopulse-full-backup-1700000000000-" + UUID.randomUUID() + ".gpb";
        Path file = Files.writeString(directory.resolve(fileName), "encrypted-content");
        when(maintenanceService.tryStartBackup("download")).thenReturn(true);
        when(maintenanceService.currentOperationId()).thenReturn("backup-operation");
        when(backupService.writeLocalBackup()).thenReturn(fileName);
        when(backupService.resolveLocalBackup(fileName)).thenReturn(file);

        Response response = resource.downloadFullBackup("203.0.113.10", null);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(maintenanceService).finishSuccess(fileName, Files.size(file));
        verify(auditLogService).logAction(any(), eq(ActionType.ADMIN_FULL_BACKUP_CREATED), any(),
                eq(fileName), argThat(details -> "backup-operation".equals(details.get("operationId"))), eq("203.0.113.10"));
        verify(auditLogService).logAction(any(), eq(ActionType.ADMIN_FULL_BACKUP_DOWNLOADED), any(),
                eq(fileName), anyMap(), eq("203.0.113.10"));

        StreamingOutput stream = (StreamingOutput) response.getEntity();
        OutputStream disconnected = new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("client disconnected"); }
        };
        assertThatThrownBy(() -> stream.write(disconnected)).isInstanceOf(IOException.class);
        verify(maintenanceService, never()).finishFailure(anyString());
    }

    @Test
    void rejectsNullLocalRestoreRequestAndReportsConcurrentOperationAsConflict() {
        assertThat(resource.restoreLocal(null, null, null).getStatus()).isEqualTo(400);
        when(maintenanceService.tryStartBackup("manual-local")).thenReturn(false);
        assertThat(resource.runBackupNow(null, null).getStatus()).isEqualTo(409);
    }

    @Test
    void doesNotExposeNativeToolOrDatabaseErrorDetailsToApiClients() throws Exception {
        when(maintenanceService.tryStartBackup("manual-local")).thenReturn(true);
        when(backupService.writeLocalBackup()).thenThrow(new IOException("raw pg_dump stderr containing secret-value"));

        Response response = resource.runBackupNow(null, null);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(((ApiResponse<?>) response.getEntity()).getMessage())
                .doesNotContain("secret-value", "stderr")
                .contains("Could not create encrypted backup");
        verify(maintenanceService).finishFailure(argThat(message -> !message.contains("secret-value")));
    }
}
