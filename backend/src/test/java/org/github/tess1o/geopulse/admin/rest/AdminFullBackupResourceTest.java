package org.github.tess1o.geopulse.admin.rest;

import io.vertx.core.http.HttpServerRequest;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupConfigDto;
import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupPasswordRequest;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.TargetType;
import org.github.tess1o.geopulse.admin.service.AdminFullBackupScheduler;
import org.github.tess1o.geopulse.admin.service.AdminFullBackupService;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.admin.service.BackupMaintenanceService;
import org.github.tess1o.geopulse.admin.service.PreparedDownloadBackup;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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
    void streamsTemporaryArchiveWithLengthAndDeletesItAfterTransfer() throws Exception {
        String fileName = "geopulse-full-backup-1700000000000-" + UUID.randomUUID() + ".gpb";
        Path file = Files.writeString(directory.resolve(fileName + ".part"), "encrypted-content");
        long size = Files.size(file);
        PreparedDownloadBackup prepared = new PreparedDownloadBackup(file, fileName, size);
        when(maintenanceService.tryStartBackup("download")).thenReturn(true);
        when(maintenanceService.currentOperationId()).thenReturn("backup-operation");
        when(backupService.writeDownloadBackup("backup-operation")).thenReturn(prepared);
        when(resource.httpRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10");

        Response response = resource.downloadFullBackup();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaderString("Content-Length")).isEqualTo(Long.toString(size));
        assertThat(response.getHeaderString("Content-Disposition")).contains(fileName);
        verify(backupService, never()).writeLocalBackup();
        verify(maintenanceService).finishSuccess(fileName, size);
        verify(maintenanceService).beginDownloadTransfer("backup-operation");
        verify(auditLogService).logAction(any(), eq(ActionType.ADMIN_FULL_BACKUP_CREATED), any(),
                eq(fileName), argThat(details -> Boolean.TRUE.equals(details.get("ephemeral"))
                        && "backup-operation".equals(details.get("operationId"))), eq("203.0.113.10"));

        StreamingOutput stream = (StreamingOutput) response.getEntity();
        ByteArrayOutputStream downloaded = new ByteArrayOutputStream();
        stream.write(downloaded);

        assertThat(downloaded.toString(StandardCharsets.UTF_8)).isEqualTo("encrypted-content");
        assertThat(file).doesNotExist();
        verify(maintenanceService).updateDownloadTransferProgress("backup-operation", size);
        verify(maintenanceService).finishDownloadTransfer("backup-operation", true);
        verify(maintenanceService, never()).finishFailure(anyString());
    }

    @Test
    void deletesTemporaryArchiveWhenClientDisconnectsWithoutFailingTheBackup() throws Exception {
        String fileName = "geopulse-full-backup-1700000000000-" + UUID.randomUUID() + ".gpb";
        Path file = Files.writeString(directory.resolve(fileName + ".part"), "encrypted-content");
        PreparedDownloadBackup prepared = new PreparedDownloadBackup(file, fileName, Files.size(file));
        when(maintenanceService.tryStartBackup("download")).thenReturn(true);
        when(maintenanceService.currentOperationId()).thenReturn("backup-operation");
        when(backupService.writeDownloadBackup("backup-operation")).thenReturn(prepared);

        Response response = resource.downloadFullBackup();
        StreamingOutput stream = (StreamingOutput) response.getEntity();
        OutputStream disconnected = new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("client disconnected"); }
        };

        assertThatThrownBy(() -> stream.write(disconnected)).isInstanceOf(IOException.class);
        assertThat(file).doesNotExist();
        verify(maintenanceService).finishDownloadTransfer("backup-operation", false);
        verify(maintenanceService, never()).finishFailure(anyString());
    }

    @Test
    void reportsConcurrentDownloadAsConflict() {
        when(maintenanceService.tryStartBackup("download")).thenReturn(false);

        assertThatThrownBy(() -> resource.downloadFullBackup())
                .isInstanceOf(GeoPulseException.class)
                .satisfies(error -> assertThat(((GeoPulseException) error).code().statusCode()).isEqualTo(409));
        verifyNoInteractions(backupService);
    }

    @Test
    void reportsMissingPasswordAsInvalidBackupRequest() throws Exception {
        when(maintenanceService.tryStartBackup("download")).thenReturn(true);
        when(maintenanceService.currentOperationId()).thenReturn("backup-operation");
        when(backupService.writeDownloadBackup("backup-operation"))
                .thenThrow(new IllegalArgumentException("Configure a backup password first"));

        assertThatThrownBy(() -> resource.downloadFullBackup())
                .isInstanceOf(GeoPulseException.class)
                .satisfies(error -> assertThat(((GeoPulseException) error).code().statusCode()).isEqualTo(400));
        verify(maintenanceService).finishFailure("Configure a backup password first");
        verify(auditLogService, never()).logAction(any(), any(), any(), any(), anyMap(), any());
    }

    @Test
    void localBackupDownloadReportsContentLength() throws Exception {
        String fileName = "geopulse-full-backup-1700000000000-" + UUID.randomUUID() + ".gpb";
        Path file = Files.writeString(directory.resolve(fileName), "existing-content");
        when(backupService.resolveLocalBackup(fileName)).thenReturn(file);

        Response response = resource.downloadLocalBackup(fileName);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaderString("Content-Length")).isEqualTo(Long.toString(Files.size(file)));
        assertThat(response.getHeaderString("Content-Disposition")).contains(fileName);
        ByteArrayOutputStream downloaded = new ByteArrayOutputStream();
        ((StreamingOutput) response.getEntity()).write(downloaded);
        assertThat(downloaded.toString(StandardCharsets.UTF_8)).isEqualTo("existing-content");
        assertThat(file).exists();
    }

    @Test
    void updatesBackupPasswordAndReturnsConfig() {
        UUID adminId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId()).thenReturn(adminId);
        AdminBackupConfigDto config = AdminBackupConfigDto.builder().passwordConfigured(true).build();
        when(backupService.getConfig()).thenReturn(config);
        when(resource.httpRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10");
        AdminBackupPasswordRequest request = new AdminBackupPasswordRequest();
        request.setPassword("twelve-chars!");

        AdminBackupConfigDto result = resource.updatePassword(request);

        assertThat(result).isSameAs(config);
        verify(backupService).updatePassword("twelve-chars!", adminId);
        verify(auditLogService).logAction(eq(adminId), eq(ActionType.SETTING_CHANGED), eq(TargetType.SETTING),
                eq("backup.password"), eq(Map.of()), eq("203.0.113.10"));
        verify(backupService, never()).updateConfig(any(), any());
    }

    @Test
    void rejectsInvalidPasswordWithoutAuditingOrReturningConfig() {
        UUID adminId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId()).thenReturn(adminId);
        doThrow(new IllegalArgumentException("Backup password must be between 12 and 1024 characters"))
                .when(backupService).updatePassword("short", adminId);
        AdminBackupPasswordRequest request = new AdminBackupPasswordRequest();
        request.setPassword("short");

        assertThatThrownBy(() -> resource.updatePassword(request))
                .isInstanceOf(GeoPulseException.class)
                .satisfies(error -> assertThat(((GeoPulseException) error).code().statusCode()).isEqualTo(400));
        verify(auditLogService, never()).logAction(any(), eq(ActionType.SETTING_CHANGED), any(), any(), anyMap(), any());
        verify(backupService, never()).getConfig();
    }

    @Test
    void rejectsNullLocalRestoreRequestAndReportsConcurrentOperationAsConflict() {
        assertThatThrownBy(() -> resource.restoreLocal(null))
                .isInstanceOf(GeoPulseException.class)
                .satisfies(error -> assertThat(((GeoPulseException) error).code().statusCode()).isEqualTo(400));
        when(maintenanceService.tryStartBackup("manual-local")).thenReturn(false);
        assertThatThrownBy(() -> resource.runBackupNow())
                .isInstanceOf(GeoPulseException.class)
                .satisfies(error -> assertThat(((GeoPulseException) error).code().statusCode()).isEqualTo(409));
    }

    @Test
    void doesNotExposeNativeToolOrDatabaseErrorDetailsToApiClients() throws Exception {
        when(maintenanceService.tryStartBackup("manual-local")).thenReturn(true);
        when(backupService.writeLocalBackup()).thenThrow(new IOException("raw pg_dump stderr containing secret-value"));

        assertThatThrownBy(() -> resource.runBackupNow())
                .isInstanceOf(GeoPulseException.class)
                .satisfies(error -> assertThat(((GeoPulseException) error).detail())
                        .doesNotContain("secret-value", "stderr")
                        .contains("Could not create encrypted backup"));
        verify(maintenanceService).finishFailure(argThat(message -> !message.contains("secret-value")));
    }
}
