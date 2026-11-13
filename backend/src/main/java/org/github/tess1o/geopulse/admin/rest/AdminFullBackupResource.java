package org.github.tess1o.geopulse.admin.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupConfigDto;
import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupCreatedResponse;
import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupFileDto;
import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupPasswordRequest;
import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupStatusDto;
import org.github.tess1o.geopulse.admin.dto.backup.RestoreAcceptedResponse;
import org.github.tess1o.geopulse.admin.dto.backup.RestoreLocalBackupRequest;
import org.github.tess1o.geopulse.admin.backup.BackupDownloadFiles;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.TargetType;
import org.github.tess1o.geopulse.admin.service.AdminFullBackupService;
import org.github.tess1o.geopulse.admin.service.AdminFullBackupScheduler;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.admin.service.BackupMaintenanceService;
import org.github.tess1o.geopulse.admin.service.PreparedDownloadBackup;
import org.github.tess1o.geopulse.auth.security.SecurityRoles;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.UserIpAddress;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.admin.backup.RestoreOperationState.PREPARING;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;


@Path("/admin/backups")
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = ApiTags.ADMIN_BACKUPS)
public class AdminFullBackupResource {
    private static final String BACKUP_FAILURE_MESSAGE =
            "Could not create encrypted backup. Verify the backup password, client tools, permissions, and free disk space.";

    @Context
    HttpServerRequest httpRequest;

    @Inject
    AdminFullBackupService backupService;

    @Inject
    BackupMaintenanceService maintenanceService;

    @Inject
    AdminFullBackupScheduler backupScheduler;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    AuditLogService auditLogService;

    @GET
    @Path("/download")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Produces("application/octet-stream")
    @APIResponse(responseCode = "200", description = "Encrypted backup file",
            content = @Content(mediaType = "application/octet-stream",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")))
    @Operation(summary = "Create and download a backup",
            description = "Creates a temporary encrypted full database backup under the backup work path and streams "
                    + "it as the response. The archive is deleted after the transfer, is not saved in the local "
                    + "backup folder, is not listed by the files endpoint, and does not affect retention. Requires a "
                    + "backup password to be configured. Fails with `409` while another backup or restore is running.")
    public Response downloadFullBackup() {
        if (!maintenanceService.tryStartBackup("download")) {
            throw new GeoPulseException(BACKUP_OPERATION_CONFLICT, "Another backup or restore is already running");
        }
        String operationId = maintenanceService.currentOperationId();
        PreparedDownloadBackup prepared = null;
        try {
            prepared = backupService.writeDownloadBackup(operationId);
            PreparedDownloadBackup backup = prepared;
            long size = backup.sizeBytes();
            java.nio.file.Path file = backup.path();
            maintenanceService.finishSuccess(backup.fileName(), size);
            maintenanceService.beginDownloadTransfer(operationId);
            StreamingOutput stream = output -> {
                try (InputStream download = Files.newInputStream(file)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = download.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                        maintenanceService.updateDownloadTransferProgress(operationId, read);
                    }
                    maintenanceService.finishDownloadTransfer(operationId, true);
                    log.info("Backup operation {} download completed; file={}", operationId, backup.fileName());
                } catch (IOException e) {
                    maintenanceService.finishDownloadTransfer(operationId, false);
                    log.warn("Backup operation {} temporary backup download was interrupted; file={}",
                            operationId, backup.fileName());
                    throw e;
                } finally {
                    BackupDownloadFiles.deleteQuietly(file);
                }
            };
            audit(ActionType.ADMIN_FULL_BACKUP_CREATED, backup.fileName(), operationId,
                    Map.of("ephemeral", true), httpRequest);
            audit(ActionType.ADMIN_FULL_BACKUP_DOWNLOADED, backup.fileName(), operationId,
                    Map.of("ephemeral", true), httpRequest);
            return Response.ok(stream)
                    .header("Content-Length", size)
                    .header("Content-Disposition", "attachment; filename=\"" + backup.fileName() + "\"")
                    .header("Content-Type", "application/octet-stream")
                    .header("Cache-Control", "no-store")
                    .build();
        } catch (IllegalArgumentException e) {
            deleteTemporary(prepared);
            maintenanceService.finishFailure(e.getMessage());
            throw new GeoPulseException(INVALID_BACKUP_REQUEST, INVALID_BACKUP_REQUEST.title(), e);
        } catch (Exception e) { // NOPMD - backup API exposes checked Exception; this try contains no application problems
            deleteTemporary(prepared);
            maintenanceService.finishFailure(BACKUP_FAILURE_MESSAGE);
            throw new GeoPulseException(BACKUP_FAILED, BACKUP_FAILURE_MESSAGE, e);
        }
    }

    @POST
    @Path("/run-now")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Operation(summary = "Create a local backup",
            description = "Creates an encrypted full database backup in the backup folder and returns its file name "
                    + "and size. The file is listed by the files endpoint and counts toward retention. Requires a "
                    + "backup password to be configured.")
    public AdminBackupCreatedResponse runBackupNow() {
        if (!maintenanceService.tryStartBackup("manual-local")) {
            throw new GeoPulseException(BACKUP_OPERATION_CONFLICT, "Another backup or restore is already running");
        }
        try {
            String operationId = maintenanceService.currentOperationId();
            String fileName = backupService.writeLocalBackup();
            long size = Files.size(backupService.resolveLocalBackup(fileName));
            maintenanceService.finishSuccess(fileName, size);
            audit(ActionType.ADMIN_FULL_BACKUP_CREATED, fileName, operationId, httpRequest);
            return new AdminBackupCreatedResponse(fileName, size);
        } catch (Exception e) { // NOPMD - backup API exposes checked Exception; this try contains no application problems
            maintenanceService.finishFailure(BACKUP_FAILURE_MESSAGE);
            throw new GeoPulseException(BACKUP_FAILED, BACKUP_FAILURE_MESSAGE, e);
        }
    }

    @GET
    @Path("/files")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Operation(summary = "List backup files",
            description = "Returns the backup files in the configured backup folder with their sizes and "
                    + "modification times.")
    public List<AdminBackupFileDto> listFiles() {
        try {
            return backupService.listLocalBackups();
        } catch (IOException e) {
            throw new GeoPulseException(BACKUP_FAILED,
                    "Failed to list local backup files. Check the configured backup folder.", e);
        }
    }

    @GET
    @Path("/files/{fileName}")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Produces("application/octet-stream")
    @APIResponse(responseCode = "200", description = "Encrypted backup file",
            content = @Content(mediaType = "application/octet-stream",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")))
    @Operation(summary = "Download a backup file",
            description = "Downloads an existing encrypted backup file from the backup folder.")
    public Response downloadLocalBackup(
            @Parameter(description = "Backup file name, as returned by `GET /api/v1/admin/backups/files`.")
            @PathParam("fileName") String fileName) {
        try {
            java.nio.file.Path file = backupService.resolveLocalBackup(fileName);
            long size = Files.size(file);
            InputStream download = Files.newInputStream(file);
            String operationId = UUID.randomUUID().toString();
            StreamingOutput stream = output -> {
                try (download) {
                    download.transferTo(output);
                    log.info("Backup download operation {} completed; file={}", operationId, fileName);
                } catch (IOException e) {
                    log.warn("Backup download operation {} was interrupted; file={}", operationId, fileName);
                    throw e;
                }
            };
            audit(ActionType.ADMIN_FULL_BACKUP_DOWNLOADED, fileName, operationId, httpRequest);
            return Response.ok(stream)
                    .header("Content-Length", size)
                    .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                    .header("Content-Type", "application/octet-stream")
                    .header("Cache-Control", "no-store")
                    .build();
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_BACKUP_REQUEST, INVALID_BACKUP_REQUEST.title(), e);
        } catch (IOException e) {
            log.warn("Could not open local backup for download; file={}", fileName, e);
            throw new GeoPulseException(BACKUP_FAILED, "Failed to open the local backup file.", e);
        }
    }

    @DELETE
    @Path("/files/{fileName}")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Operation(summary = "Delete a backup file",
            description = "Deletes a backup file from the backup folder. Not allowed while a backup or restore is "
                    + "running.")
    public void deleteLocalBackup(
            @Parameter(description = "Backup file name, as returned by `GET /api/v1/admin/backups/files`.")
            @PathParam("fileName") String fileName) {
        if (!maintenanceService.tryStartFileMutation("delete")) {
            throw new GeoPulseException(BACKUP_OPERATION_CONFLICT,
                    "Cannot delete a backup while another backup or restore is running");
        }
        try {
            String operationId = UUID.randomUUID().toString();
            backupService.deleteLocalBackup(fileName);
            audit(ActionType.ADMIN_FULL_BACKUP_DELETED, fileName, operationId, httpRequest);
            log.info("Backup file operation {} deleted {}", operationId, fileName);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_BACKUP_REQUEST, INVALID_BACKUP_REQUEST.title(), e);
        } catch (IOException e) {
            throw new GeoPulseException(BACKUP_FAILED,
                    "Failed to delete the local backup. Check backup folder permissions.", e);
        } finally {
            maintenanceService.finishFileMutation();
        }
    }

    @POST
    @Path("/restore/upload")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @APIResponseSchema(value = RestoreAcceptedResponse.class, responseCode = "202",
            responseDescription = "Restore preparation accepted")
    @Operation(summary = "Restore an uploaded backup",
            description = "Starts restoring an uploaded backup file (multipart fields `file` and `password`). "
                    + "GeoPulse prepares the restore in a separate database while the application keeps running, "
                    + "then switches to it and the backend process exits so it can be restarted on the restored "
                    + "data. All current data is replaced. Poll `GET /api/v1/admin/backups/status` for progress. "
                    + "See [Backup and "
                    + "Restore](https://geopulse.cc/docs/system-administration/maintenance/backup-restore).")
    public Response restoreUploaded(@RestForm("file") FileUpload file,
                                    @RestForm("password") String password) {
        if (file == null || file.uploadedFile() == null) {
            throw new GeoPulseException(INVALID_BACKUP_REQUEST, "Backup file is required");
        }
        return prepareRestore(file.uploadedFile(), file.fileName(), password, httpRequest);
    }

    @POST
    @Path("/restore/local")
    @RolesAllowed(SecurityRoles.ADMIN)
    @APIResponseSchema(value = RestoreAcceptedResponse.class, responseCode = "202",
            responseDescription = "Restore preparation accepted")
    @Operation(summary = "Restore a backup file",
            description = "Starts restoring a backup file that is already in the backup folder, with the password "
                    + "used to create it. Works like restoring an uploaded backup: all current data is replaced and "
                    + "the backend restarts. See [Backup and "
                    + "Restore](https://geopulse.cc/docs/system-administration/maintenance/backup-restore).")
    public Response restoreLocal(RestoreLocalBackupRequest request) {
        if (request == null) {
            throw new GeoPulseException(INVALID_BACKUP_REQUEST, "Restore request is required");
        }
        try {
            java.nio.file.Path file = backupService.resolveLocalBackup(request.getFileName());
            return prepareRestore(file, request.getFileName(), request.getPassword(), httpRequest);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_BACKUP_REQUEST, INVALID_BACKUP_REQUEST.title(), e);
        }
    }

    @GET
    @Path("/config")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Operation(summary = "Get backup settings",
            description = "Returns the backup settings: whether a password is configured (the password itself is "
                    + "never returned), scheduled backup and cron expression, backup folder, retention count, "
                    + "operation timeout, and backup health alerts.")
    public AdminBackupConfigDto getConfig() {
        return backupService.getConfig();
    }

    @PUT
    @Path("/config")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Operation(summary = "Update backup settings",
            description = "Updates the backup settings and reschedules automatic backups. A new password applies to "
                    + "new backups only.")
    public AdminBackupConfigDto updateConfig(AdminBackupConfigDto config) {
        try {
            backupService.validateConfig(config);
            backupScheduler.validateSchedule(config);
            backupService.updateConfig(config, currentUserService.getCurrentUserId());
            backupScheduler.rescheduleFromConfig();
            return backupService.getConfig();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new GeoPulseException(INVALID_BACKUP_REQUEST, INVALID_BACKUP_REQUEST.title(), e);
        }
    }

    @PUT
    @Path("/password")
    @RolesAllowed(SecurityRoles.ADMIN)
    @APIResponseSchema(value = AdminBackupConfigDto.class, responseCode = "200",
            responseDescription = "Backup password updated")
    @Operation(summary = "Set or change the backup password",
            description = "Updates the password used to encrypt new full backups. This does not require a valid "
                    + "scheduled-backup configuration or a writable backup folder. Changing the password affects new "
                    + "backups only.")
    public AdminBackupConfigDto updatePassword(AdminBackupPasswordRequest request) {
        UUID adminId = currentUserService.getCurrentUserId();
        try {
            backupService.updatePassword(request == null ? null : request.getPassword(), adminId);
            auditSettingChange(adminId, "backup.password");
            return backupService.getConfig();
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_BACKUP_REQUEST, INVALID_BACKUP_REQUEST.title(), e);
        }
    }

    @GET
    @Path("/status")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Operation(summary = "Get backup and restore status",
            description = "Returns the state of the current or last backup or restore operation, including restore "
                    + "preparation and activation progress and errors.")
    public AdminBackupStatusDto status() {
        return maintenanceService.getStatus();
    }

    @POST
    @Path("/restore/retry")
    @RolesAllowed(SecurityRoles.ADMIN)
    @APIResponseSchema(value = AdminBackupStatusDto.class, responseCode = "202",
            responseDescription = "Restore activation retry accepted")
    @Operation(summary = "Retry restore activation",
            description = "Retries switching to a prepared restore after activation could not complete, for example "
                    + "after an interrupted cutover that PostgreSQL rolled back. See [Backup and "
                    + "Restore](https://geopulse.cc/docs/system-administration/maintenance/backup-restore).")
    public Response retryPreparedRestore() {
        try {
            String operationId = backupService.retryActivation();
            audit(ActionType.ADMIN_FULL_RESTORE_RETRIED, maintenanceService.getStatus().getFileName(), operationId, httpRequest);
            return Response.accepted(maintenanceService.getStatus()).build();
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(RESTORE_OPERATION_CONFLICT, RESTORE_OPERATION_CONFLICT.title(), e);
        }
    }

    @POST
    @Path("/restore/discard")
    @RolesAllowed(SecurityRoles.ADMIN)
    @APIResponseSchema(value = AdminBackupStatusDto.class, responseCode = "200",
            responseDescription = "Prepared restore discarded")
    @Operation(summary = "Discard a prepared restore",
            description = "Deletes a prepared restore that was not activated and returns to normal operation on the "
                    + "current data.")
    public Response discardPreparedRestore() {
        try {
            String fileName = maintenanceService.getStatus().getFileName();
            String operationId = backupService.discardPrepared();
            audit(ActionType.ADMIN_FULL_RESTORE_DISCARDED, fileName, operationId, httpRequest);
            return Response.ok(maintenanceService.getStatus()).build();
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(RESTORE_OPERATION_CONFLICT, RESTORE_OPERATION_CONFLICT.title(), e);
        } catch (Exception e) { // NOPMD - discardPrepared exposes checked Exception after classified state errors
            throw new GeoPulseException(RESTORE_PREPARATION_FAILED,
                    "Could not discard staging. The application remains blocked; check database connections and permissions.", e);
        }
    }

    private Response prepareRestore(java.nio.file.Path source, String fileName, String password, HttpServerRequest httpRequest) {
        try {
            String operationId = backupService.startRestore(source, fileName, password);
            audit(ActionType.ADMIN_FULL_BACKUP_IMPORTED, fileName, operationId, httpRequest);
            return Response.accepted(new RestoreAcceptedResponse(operationId, PREPARING)).build();
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_BACKUP_REQUEST, INVALID_BACKUP_REQUEST.title(), e);
        } catch (IllegalStateException e) {
            throw new GeoPulseException(RESTORE_OPERATION_CONFLICT, RESTORE_OPERATION_CONFLICT.title(), e);
        } catch (Exception e) { // NOPMD - startRestore exposes checked Exception after classified input/state errors
            throw new GeoPulseException(RESTORE_PREPARATION_FAILED,
                    "Could not prepare the restore. Check persistent working storage and database permissions.", e);
        }
    }

    private void audit(ActionType actionType, String fileName, String operationId, HttpServerRequest httpRequest) {
        audit(actionType, fileName, operationId, Map.of(), httpRequest);
    }

    private void audit(ActionType actionType, String fileName, String operationId,
                       Map<String, Object> extraDetails, HttpServerRequest httpRequest) {
        try {
            Map<String, Object> details = new HashMap<>(extraDetails);
            details.put("fileName", fileName == null ? "" : fileName);
            details.put("operationId", operationId);
            auditLogService.logAction(
                    currentUserService.getCurrentUserId(),
                    actionType,
                    TargetType.BACKUP,
                    fileName,
                    details,
                    UserIpAddress.resolve(httpRequest)
            );
        } catch (Exception e) {
            log.warn("Failed to write backup audit log for {} {}", actionType, fileName, e);
        }
    }

    private void auditSettingChange(UUID adminId, String key) {
        try {
            auditLogService.logAction(adminId, ActionType.SETTING_CHANGED, TargetType.SETTING,
                    key, Map.of(), UserIpAddress.resolve(httpRequest));
        } catch (Exception e) {
            log.warn("Failed to write setting audit log for {}", key, e);
        }
    }

    private void deleteTemporary(PreparedDownloadBackup prepared) {
        if (prepared != null) {
            BackupDownloadFiles.deleteQuietly(prepared.path());
        }
    }
}
