package org.github.tess1o.geopulse.admin.rest;

import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupConfigDto;
import org.github.tess1o.geopulse.admin.dto.backup.RestoreLocalBackupRequest;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.TargetType;
import org.github.tess1o.geopulse.admin.service.AdminFullBackupService;
import org.github.tess1o.geopulse.admin.service.AdminFullBackupScheduler;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.admin.service.BackupMaintenanceService;
import org.github.tess1o.geopulse.auth.security.SecurityRoles;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.shared.api.UserIpAddress;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.nio.file.Files;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipOutputStream;

@Path("/api/admin/backups")
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class AdminFullBackupResource {
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
    @Produces("application/zip")
    public Response downloadFullBackup(@HeaderParam("X-Forwarded-For") String forwardedFor,
                                       @HeaderParam("X-Real-IP") String realIp) {
        if (!maintenanceService.tryStartBackup("download")) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(ApiResponse.error("Another backup or restore is already running"))
                    .build();
        }
        String fileName = "geopulse-full-backup-" + Instant.now().getEpochSecond() + ".zip";
        StreamingOutput stream = output -> {
            try (ZipOutputStream zos = new ZipOutputStream(output)) {
                backupService.writeFullBackup(zos);
                maintenanceService.finishSuccess(fileName, null);
                audit(ActionType.ADMIN_FULL_BACKUP_EXPORTED, fileName, forwardedFor, realIp);
            } catch (Exception e) {
                maintenanceService.finishFailure(e.getMessage());
                throw e;
            }
        };
        return Response.ok(stream)
                .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                .header("Content-Type", "application/zip")
                .build();
    }

    @POST
    @Path("/run-now")
    @RolesAllowed(SecurityRoles.ADMIN)
    public Response runBackupNow(@HeaderParam("X-Forwarded-For") String forwardedFor,
                                 @HeaderParam("X-Real-IP") String realIp) {
        if (!maintenanceService.tryStartBackup("manual-local")) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(ApiResponse.error("Another backup or restore is already running"))
                    .build();
        }
        try {
            String fileName = backupService.writeLocalBackup();
            long size = Files.size(backupService.resolveLocalBackup(fileName));
            maintenanceService.finishSuccess(fileName, size);
            audit(ActionType.ADMIN_FULL_BACKUP_EXPORTED, fileName, forwardedFor, realIp);
            return Response.ok(ApiResponse.success(Map.of("fileName", fileName, "sizeBytes", size))).build();
        } catch (Exception e) {
            log.error("Manual full backup failed", e);
            maintenanceService.finishFailure(e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to create full backup: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/files")
    @RolesAllowed(SecurityRoles.ADMIN)
    public Response listFiles() {
        try {
            return Response.ok(ApiResponse.success(backupService.listLocalBackups())).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to list backup files: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/files/{fileName}")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Produces("application/zip")
    public Response downloadLocalBackup(@PathParam("fileName") String fileName) {
        try {
            java.nio.file.Path file = backupService.resolveLocalBackup(fileName);
            StreamingOutput stream = output -> Files.copy(file, output);
            return Response.ok(stream)
                    .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                    .header("Content-Type", "application/zip")
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ApiResponse.error(e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/files/{fileName}")
    @RolesAllowed(SecurityRoles.ADMIN)
    public Response deleteLocalBackup(@PathParam("fileName") String fileName,
                                      @HeaderParam("X-Forwarded-For") String forwardedFor,
                                      @HeaderParam("X-Real-IP") String realIp) {
        if (maintenanceService.getStatus().isBackupRunning() || maintenanceService.getStatus().isRestoreRunning()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(ApiResponse.error("Cannot delete a backup while another backup or restore is running"))
                    .build();
        }
        try {
            backupService.deleteLocalBackup(fileName);
            audit(ActionType.ADMIN_FULL_BACKUP_DELETED, fileName, forwardedFor, realIp);
            return Response.ok(ApiResponse.success(Map.of("fileName", fileName))).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ApiResponse.error(e.getMessage())).build();
        } catch (Exception e) {
            log.error("Failed to delete local backup {}", fileName, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to delete local backup: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/restore/upload")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response restoreUploaded(@RestForm("file") FileUpload file,
                                    @HeaderParam("X-Forwarded-For") String forwardedFor,
                                    @HeaderParam("X-Real-IP") String realIp) {
        if (file == null || file.uploadedFile() == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ApiResponse.error("Backup file is required")).build();
        }
        try {
            return restoreBytes(Files.readAllBytes(file.uploadedFile()), file.fileName(), forwardedFor, realIp);
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to read uploaded backup: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/restore/local")
    @RolesAllowed(SecurityRoles.ADMIN)
    public Response restoreLocal(RestoreLocalBackupRequest request,
                                 @HeaderParam("X-Forwarded-For") String forwardedFor,
                                 @HeaderParam("X-Real-IP") String realIp) {
        try {
            java.nio.file.Path file = backupService.resolveLocalBackup(request.getFileName());
            return restoreBytes(Files.readAllBytes(file), request.getFileName(), forwardedFor, realIp);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ApiResponse.error(e.getMessage())).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to restore local backup: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/config")
    @RolesAllowed(SecurityRoles.ADMIN)
    public Response getConfig() {
        return Response.ok(ApiResponse.success(backupService.getConfig())).build();
    }

    @PUT
    @Path("/config")
    @RolesAllowed(SecurityRoles.ADMIN)
    public Response updateConfig(AdminBackupConfigDto config) {
        try {
            backupService.validateConfig(config);
            backupScheduler.validateSchedule(config);
            backupService.updateConfig(config, currentUserService.getCurrentUserId());
            backupScheduler.rescheduleFromConfig();
            return Response.ok(ApiResponse.success(backupService.getConfig())).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ApiResponse.error(e.getMessage())).build();
        }
    }

    @GET
    @Path("/status")
    @RolesAllowed(SecurityRoles.ADMIN)
    public Response status() {
        return Response.ok(ApiResponse.success(maintenanceService.getStatus())).build();
    }

    private Response restoreBytes(byte[] backupBytes, String fileName, String forwardedFor, String realIp) {
        if (!maintenanceService.tryStartRestore("restore", fileName)) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(ApiResponse.error("Another backup or restore is already running"))
                    .build();
        }
        try {
            UUID adminId = currentUserService.getCurrentUserId();
            backupService.restoreFullBackup(backupBytes, adminId);
            maintenanceService.finishSuccess(fileName, (long) backupBytes.length);
            audit(ActionType.ADMIN_FULL_BACKUP_IMPORTED, fileName, forwardedFor, realIp);
            return Response.ok(ApiResponse.success(Map.of("fileName", fileName))).build();
        } catch (IllegalArgumentException e) {
            maintenanceService.finishFailure(e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST).entity(ApiResponse.error(e.getMessage())).build();
        } catch (Exception e) {
            log.error("Full backup restore failed", e);
            maintenanceService.finishFailure(e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to restore full backup: " + e.getMessage()))
                    .build();
        }
    }

    private void audit(ActionType actionType, String fileName, String forwardedFor, String realIp) {
        auditLogService.logAction(
                currentUserService.getCurrentUserId(),
                actionType,
                TargetType.BACKUP,
                fileName,
                Map.of("fileName", fileName),
                UserIpAddress.resolve(httpRequest, forwardedFor, realIp)
        );
    }
}
