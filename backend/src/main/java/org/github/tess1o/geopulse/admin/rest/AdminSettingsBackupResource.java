package org.github.tess1o.geopulse.admin.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.admin.dto.AdminSettingsBackupDto;
import org.github.tess1o.geopulse.admin.dto.AdminSettingsImportResult;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.TargetType;
import org.github.tess1o.geopulse.admin.service.AdminSettingsBackupService;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.auth.security.SecurityRoles;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.shared.api.UserIpAddress;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.nio.file.Files;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Path("/api/admin/settings-backup")
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = "Admin: Settings Backup", description = "Export and import admin-configurable global settings.")
public class AdminSettingsBackupResource {

    @Context
    HttpServerRequest httpRequest;

    @Inject
    AdminSettingsBackupService backupService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AuditLogService auditLogService;

    @Inject
    CurrentUserService currentUserService;

    @GET
    @Path("/export")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Produces(MediaType.APPLICATION_JSON)
    public Response exportSettingsBackup(
            @jakarta.ws.rs.HeaderParam("X-Forwarded-For") String forwardedFor,
            @jakarta.ws.rs.HeaderParam("X-Real-IP") String realIp) {
        try {
            AdminSettingsBackupDto backup = backupService.exportBackup();
            byte[] payload = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(backup);
            UUID adminId = currentUserService.getCurrentUserId();
            auditLogService.logAction(
                    adminId,
                    ActionType.ADMIN_SETTINGS_EXPORTED,
                    TargetType.SETTING,
                    "admin-settings-backup",
                    Map.of(
                            "settings", backup.getSettings().size(),
                            "oidcProviders", backup.getOidcProviders().size(),
                            "customGeocodingProviders", backup.getCustomGeocodingProviders().size()
                    ),
                    UserIpAddress.resolve(httpRequest, forwardedFor, realIp)
            );

            return Response.ok(payload)
                    .header("Content-Disposition", "attachment; filename=\"geopulse-admin-settings-"
                            + Instant.now().getEpochSecond() + ".json\"")
                    .header("Content-Type", "application/json; charset=utf-8")
                    .build();
        } catch (Exception e) {
            log.error("Failed to export admin settings backup", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to export admin settings backup"))
                    .build();
        }
    }

    @POST
    @Path("/import")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importSettingsBackup(
            @RestForm("file") FileUpload file,
            @jakarta.ws.rs.HeaderParam("X-Forwarded-For") String forwardedFor,
            @jakarta.ws.rs.HeaderParam("X-Real-IP") String realIp) {
        if (file == null || file.uploadedFile() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Admin settings backup file is required"))
                    .build();
        }

        try {
            AdminSettingsBackupDto backup = objectMapper.readValue(
                    Files.readAllBytes(file.uploadedFile()),
                    AdminSettingsBackupDto.class);
            UUID adminId = currentUserService.getCurrentUserId();
            AdminSettingsImportResult result = backupService.importBackup(backup, adminId);
            auditLogService.logAction(
                    adminId,
                    ActionType.ADMIN_SETTINGS_IMPORTED,
                    TargetType.SETTING,
                    "admin-settings-backup",
                    auditDetails(result),
                    UserIpAddress.resolve(httpRequest, forwardedFor, realIp)
            );
            return Response.ok(ApiResponse.success(result)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to import admin settings backup", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to import admin settings backup"))
                    .build();
        }
    }

    private Map<String, Object> auditDetails(AdminSettingsImportResult result) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("settingsImported", result.getSettingsImported());
        details.put("oidcProvidersImported", result.getOidcProvidersImported());
        details.put("oidcProvidersRemoved", result.getOidcProvidersRemoved());
        details.put("oidcEnvironmentOverridesCreated", result.getOidcEnvironmentOverridesCreated());
        details.put("customGeocodingProvidersImported", result.getCustomGeocodingProvidersImported());
        details.put("customGeocodingProvidersRemoved", result.getCustomGeocodingProvidersRemoved());
        details.put("unsupportedSettings", result.getUnsupportedSettings());
        return details;
    }
}
