package org.github.tess1o.geopulse.admin.rest;

import io.vertx.core.http.HttpServerRequest;
import io.quarkiverse.httpproblem.HttpProblem;
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
import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupConfigDto;
import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupCreatedResponse;
import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupFileDto;
import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupStatusDto;
import org.github.tess1o.geopulse.admin.dto.backup.RestoreAcceptedResponse;
import org.github.tess1o.geopulse.admin.dto.backup.RestoreLocalBackupRequest;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.TargetType;
import org.github.tess1o.geopulse.admin.service.AdminFullBackupService;
import org.github.tess1o.geopulse.admin.service.AdminFullBackupScheduler;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.admin.service.BackupMaintenanceService;
import org.github.tess1o.geopulse.auth.security.SecurityRoles;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.UserIpAddress;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.github.tess1o.geopulse.admin.backup.RestoreOperationState.PREPARING;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;


@Path("/admin/backups")
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = "Admin: Backups", description = "Create and restore native full backups.")
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
    public Response downloadFullBackup() {
        if (!maintenanceService.tryStartBackup("download")) {
            throw problem(BACKUP_OPERATION_CONFLICT, "Another backup or restore is already running");
        }
        try {
            String operationId = maintenanceService.currentOperationId();
            String fileName = backupService.writeLocalBackup();
            java.nio.file.Path completed = backupService.resolveLocalBackup(fileName);
            long size = Files.size(completed);
            InputStream download = Files.newInputStream(completed);
            maintenanceService.finishSuccess(fileName, size);
            StreamingOutput stream = output -> {
                try (download) {
                    download.transferTo(output);
                    log.info("Backup operation {} download completed; file={}", operationId, fileName);
                } catch (IOException e) {
                    log.warn("Backup operation {} file was created but browser download was interrupted; file={}",
                            operationId, fileName);
                    throw e;
                }
            };
            audit(ActionType.ADMIN_FULL_BACKUP_CREATED, fileName, operationId, httpRequest);
            audit(ActionType.ADMIN_FULL_BACKUP_DOWNLOADED, fileName, operationId, httpRequest);
            return Response.ok(stream).header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                    .header("Content-Type", "application/octet-stream").build();
        } catch (Exception e) {
            log.error("Full backup download creation failed; failureType={}", e.getClass().getSimpleName());
            maintenanceService.finishFailure(BACKUP_FAILURE_MESSAGE);
            throw problem(BACKUP_FAILED, BACKUP_FAILURE_MESSAGE);
        }
    }

    @POST
    @Path("/run-now")
    @RolesAllowed(SecurityRoles.ADMIN)
    public AdminBackupCreatedResponse runBackupNow() {
        if (!maintenanceService.tryStartBackup("manual-local")) {
            throw problem(BACKUP_OPERATION_CONFLICT, "Another backup or restore is already running");
        }
        try {
            String operationId = maintenanceService.currentOperationId();
            String fileName = backupService.writeLocalBackup();
            long size = Files.size(backupService.resolveLocalBackup(fileName));
            maintenanceService.finishSuccess(fileName, size);
            audit(ActionType.ADMIN_FULL_BACKUP_CREATED, fileName, operationId, httpRequest);
            return new AdminBackupCreatedResponse(fileName, size);
        } catch (Exception e) {
            log.error("Manual full backup failed; failureType={}", e.getClass().getSimpleName());
            maintenanceService.finishFailure(BACKUP_FAILURE_MESSAGE);
            throw problem(BACKUP_FAILED, BACKUP_FAILURE_MESSAGE);
        }
    }

    @GET
    @Path("/files")
    @RolesAllowed(SecurityRoles.ADMIN)
    public List<AdminBackupFileDto> listFiles() {
        try {
            return backupService.listLocalBackups();
        } catch (Exception e) {
            log.warn("Failed to list local backup files", e);
            throw problem(BACKUP_FAILED,
                    "Failed to list local backup files. Check the configured backup folder.");
        }
    }

    @GET
    @Path("/files/{fileName}")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Produces("application/octet-stream")
    @APIResponse(responseCode = "200", description = "Encrypted backup file",
            content = @Content(mediaType = "application/octet-stream",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")))
    public Response downloadLocalBackup(@PathParam("fileName") String fileName) {
        try {
            java.nio.file.Path file = backupService.resolveLocalBackup(fileName);
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
                    .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                    .header("Content-Type", "application/octet-stream")
                    .build();
        } catch (IllegalArgumentException e) {
            throw problem(INVALID_BACKUP_REQUEST, e.getMessage());
        } catch (IOException e) {
            log.warn("Could not open local backup for download; file={}", fileName, e);
            throw problem(BACKUP_FAILED, "Failed to open the local backup file.");
        }
    }

    @DELETE
    @Path("/files/{fileName}")
    @RolesAllowed(SecurityRoles.ADMIN)
    public void deleteLocalBackup(@PathParam("fileName") String fileName) {
        if (!maintenanceService.tryStartFileMutation("delete")) {
            throw problem(BACKUP_OPERATION_CONFLICT,
                    "Cannot delete a backup while another backup or restore is running");
        }
        try {
            String operationId = UUID.randomUUID().toString();
            backupService.deleteLocalBackup(fileName);
            audit(ActionType.ADMIN_FULL_BACKUP_DELETED, fileName, operationId, httpRequest);
            log.info("Backup file operation {} deleted {}", operationId, fileName);
        } catch (IllegalArgumentException e) {
            throw problem(INVALID_BACKUP_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to delete local backup {}", fileName, e);
            throw problem(BACKUP_FAILED,
                    "Failed to delete the local backup. Check backup folder permissions.");
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
    public Response restoreUploaded(@RestForm("file") FileUpload file,
                                    @RestForm("password") String password) {
        if (file == null || file.uploadedFile() == null) {
            throw problem(INVALID_BACKUP_REQUEST, "Backup file is required");
        }
        try {
            return prepareRestore(file.uploadedFile(), file.fileName(), password, httpRequest);
        } catch (HttpProblem e) {
            throw e;
        } catch (Exception e) {
            log.warn("Could not read uploaded full backup", e);
            throw problem(RESTORE_PREPARATION_FAILED, "Failed to read the uploaded backup.");
        }
    }

    @POST
    @Path("/restore/local")
    @RolesAllowed(SecurityRoles.ADMIN)
    @APIResponseSchema(value = RestoreAcceptedResponse.class, responseCode = "202",
            responseDescription = "Restore preparation accepted")
    public Response restoreLocal(RestoreLocalBackupRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Restore request is required");
            java.nio.file.Path file = backupService.resolveLocalBackup(request.getFileName());
            return prepareRestore(file, request.getFileName(), request.getPassword(), httpRequest);
        } catch (HttpProblem e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw problem(INVALID_BACKUP_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.warn("Could not open selected local backup for restore", e);
            throw problem(RESTORE_PREPARATION_FAILED, "Failed to open the selected local backup.");
        }
    }

    @GET
    @Path("/config")
    @RolesAllowed(SecurityRoles.ADMIN)
    public AdminBackupConfigDto getConfig() {
        return backupService.getConfig();
    }

    @PUT
    @Path("/config")
    @RolesAllowed(SecurityRoles.ADMIN)
    public AdminBackupConfigDto updateConfig(AdminBackupConfigDto config) {
        try {
            backupService.validateConfig(config);
            backupScheduler.validateSchedule(config);
            backupService.updateConfig(config, currentUserService.getCurrentUserId());
            backupScheduler.rescheduleFromConfig();
            return backupService.getConfig();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw problem(INVALID_BACKUP_REQUEST, e.getMessage());
        }
    }

    @GET
    @Path("/status")
    @RolesAllowed(SecurityRoles.ADMIN)
    public AdminBackupStatusDto status() {
        return maintenanceService.getStatus();
    }

    @POST
    @Path("/restore/retry")
    @RolesAllowed(SecurityRoles.ADMIN)
    @APIResponseSchema(value = AdminBackupStatusDto.class, responseCode = "202",
            responseDescription = "Restore activation retry accepted")
    public Response retryPreparedRestore() {
        try {
            String operationId = backupService.retryActivation();
            audit(ActionType.ADMIN_FULL_RESTORE_RETRIED, maintenanceService.getStatus().getFileName(), operationId, httpRequest);
            return Response.accepted(maintenanceService.getStatus()).build();
        } catch (IllegalArgumentException e) {
            throw problem(RESTORE_OPERATION_CONFLICT, e.getMessage());
        }
    }

    @POST
    @Path("/restore/discard")
    @RolesAllowed(SecurityRoles.ADMIN)
    @APIResponseSchema(value = AdminBackupStatusDto.class, responseCode = "200",
            responseDescription = "Prepared restore discarded")
    public Response discardPreparedRestore() {
        try {
            String fileName = maintenanceService.getStatus().getFileName();
            String operationId = backupService.discardPrepared();
            audit(ActionType.ADMIN_FULL_RESTORE_DISCARDED, fileName, operationId, httpRequest);
            return Response.ok(maintenanceService.getStatus()).build();
        } catch (IllegalArgumentException e) {
            throw problem(RESTORE_OPERATION_CONFLICT, e.getMessage());
        } catch (Exception e) {
            throw problem(RESTORE_PREPARATION_FAILED,
                    "Could not discard staging. The application remains blocked; check database connections and permissions.");
        }
    }

    private Response prepareRestore(java.nio.file.Path source, String fileName, String password, HttpServerRequest httpRequest) {
        try {
            String operationId = backupService.startRestore(source, fileName, password);
            audit(ActionType.ADMIN_FULL_BACKUP_IMPORTED, fileName, operationId, httpRequest);
            return Response.accepted(new RestoreAcceptedResponse(operationId, PREPARING)).build();
        } catch (IllegalArgumentException e) {
            throw problem(INVALID_BACKUP_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw problem(RESTORE_OPERATION_CONFLICT, e.getMessage());
        } catch (Exception e) {
            throw problem(RESTORE_PREPARATION_FAILED,
                    "Could not prepare the restore. Check persistent working storage and database permissions.");
        }
    }

    private void audit(ActionType actionType, String fileName, String operationId, HttpServerRequest httpRequest) {
        try {
            auditLogService.logAction(
                    currentUserService.getCurrentUserId(),
                    actionType,
                    TargetType.BACKUP,
                    fileName,
                    Map.of("fileName", fileName == null ? "" : fileName, "operationId", operationId),
                    UserIpAddress.resolve(httpRequest)
            );
        } catch (Exception e) {
            log.warn("Failed to write backup audit log for {} {}", actionType, fileName, e);
        }
    }
}
