package org.github.tess1o.geopulse.admin.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.github.tess1o.geopulse.admin.dto.UpdateSettingRequest;
import org.github.tess1o.geopulse.admin.model.SettingInfo;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST resource for admin settings management.
 */
@Path("/api/admin/settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
@Slf4j
public class AdminSettingsResource {

    @Inject
    SystemSettingsService settingsService;

    @Inject
    AuditLogService auditLogService;

    @Inject
    CurrentUserService currentUserService;

    /**
     * Get all settings grouped by category.
     */
    @GET
    public Response getAllSettings() {
        Map<String, List<SettingInfo>> settings = settingsService.getAllSettings();
        return Response.ok(settings).build();
    }

    /**
     * Get settings for a specific category.
     */
    @GET
    @Path("/{category}")
    public Response getSettingsByCategory(@PathParam("category") String category) {
        List<SettingInfo> settings = settingsService.getSettingsByCategory(category);
        return Response.ok(settings).build();
    }

    /**
     * Update a setting value.
     */
    @PUT
    @Path("/{key}")
    public Response updateSetting(
            @PathParam("key") String key,
            UpdateSettingRequest request,
            @HeaderParam("X-Forwarded-For") String forwardedFor,
            @HeaderParam("X-Real-IP") String realIp) {

        UUID adminId = currentUserService.getCurrentUserId();
        String oldValue = settingsService.getString(key);

        settingsService.setValue(key, request.getValue(), adminId);

        // Audit log
        String ipAddress = forwardedFor != null ? forwardedFor : realIp;
        auditLogService.logSettingChange(adminId, key, oldValue, request.getValue(), ipAddress);

        return Response.ok(Map.of("success", true)).build();
    }

    /**
     * Reset a setting to default (delete from DB).
     */
    @DELETE
    @Path("/{key}")
    public Response resetSetting(
            @PathParam("key") String key,
            @HeaderParam("X-Forwarded-For") String forwardedFor,
            @HeaderParam("X-Real-IP") String realIp) {

        UUID adminId = currentUserService.getCurrentUserId();
        String oldValue = settingsService.getString(key);

        settingsService.resetToDefault(key);

        // Audit log
        String ipAddress = forwardedFor != null ? forwardedFor : realIp;
        auditLogService.logSettingReset(adminId, key, oldValue, ipAddress);

        return Response.ok(Map.of("success", true, "defaultValue", settingsService.getDefaultValue(key))).build();
    }
}
