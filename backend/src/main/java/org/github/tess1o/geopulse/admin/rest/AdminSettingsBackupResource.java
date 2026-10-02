package org.github.tess1o.geopulse.admin.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.admin.dto.AdminSettingsBackupDto;
import org.github.tess1o.geopulse.admin.dto.AdminSettingsImportResult;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.TargetType;
import org.github.tess1o.geopulse.admin.service.AdminSettingsBackupService;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.auth.security.SecurityRoles;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.ApiPaths;
import org.github.tess1o.geopulse.shared.api.UserIpAddress;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.nio.file.Files;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

@Path(ApiPaths.ADMIN_SETTINGS_BACKUPS)
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = "Admin: Backups", description = "Export and import admin-configurable global settings.")
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
    @Path("/exports")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponseSchema(value = AdminSettingsBackupDto.class, responseCode = "200",
            responseDescription = "Admin settings backup")
    public Response exportSettingsBackup() {
        AdminSettingsBackupDto backup = backupService.exportBackup();
        byte[] payload;
        try {
            payload = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(backup);
        } catch (JsonProcessingException e) {
            throw new GeoPulseException(INTERNAL_ERROR, "Failed to export admin settings backup", e);
        }
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
                UserIpAddress.resolve(httpRequest)
        );

        return Response.ok(payload)
                .header("Content-Disposition", "attachment; filename=\"geopulse-admin-settings-"
                        + Instant.now().getEpochSecond() + ".json\"")
                .header("Content-Type", "application/json; charset=utf-8")
                .build();
    }

    @POST
    @Path("/imports")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public AdminSettingsImportResult importSettingsBackup(@RestForm("file") FileUpload file) {
        if (file == null || file.uploadedFile() == null) {
            throw new GeoPulseException(INVALID_ADMIN_SETTINGS_BACKUP, "Admin settings backup file is required");
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
                    UserIpAddress.resolve(httpRequest)
            );
            return result;
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_ADMIN_SETTINGS_BACKUP, INVALID_ADMIN_SETTINGS_BACKUP.title(), e);
        } catch (java.io.IOException e) {
            throw new GeoPulseException(INTERNAL_ERROR, "Failed to import admin settings backup", e);
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
