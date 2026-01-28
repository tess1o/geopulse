package org.github.tess1o.geopulse.admin.rest;

import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.dto.BulkUpdateRequest;
import org.github.tess1o.geopulse.admin.dto.UpdateSettingRequest;
import org.github.tess1o.geopulse.admin.model.SettingInfo;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.admin.service.GeocodingValidationService;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.UserIpAddress;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST resource for admin settings management.
 */
@Path("/api/admin/settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
@Slf4j
public class AdminSettingsResource {

    @Context
    HttpServerRequest httpRequest;

    @Inject
    SystemSettingsService settingsService;

    @Inject
    AuditLogService auditLogService;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    GeocodingValidationService geocodingValidationService;

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
     * <p>
     * Note: Geocoding settings should use the bulk update endpoint to ensure
     * proper validation of interdependent settings. This endpoint is primarily
     * used for other categories (auth, ai, import, export).
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
        String ipAddress = UserIpAddress.resolve(httpRequest, forwardedFor, realIp);
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
        String ipAddress = UserIpAddress.resolve(httpRequest, forwardedFor, realIp);
        auditLogService.logSettingReset(adminId, key, oldValue, ipAddress);

        return Response.ok(Map.of("success", true, "defaultValue", settingsService.getDefaultValue(key))).build();
    }

    /**
     * Bulk update multiple settings atomically.
     * All settings are validated before any changes are made.
     * If any validation fails, no settings are updated (transaction rollback).
     * <p>
     * This endpoint is particularly important for geocoding settings, which have
     * interdependencies (e.g., enabling a provider requires both a flag and credentials).
     * Context-aware validation ensures all pending changes are considered together.
     */
    @POST
    @Path("/bulk")
    @Transactional
    public Response bulkUpdateSettings(
            BulkUpdateRequest request,
            @HeaderParam("X-Forwarded-For") String forwardedFor,
            @HeaderParam("X-Real-IP") String realIp) {

        UUID adminId = currentUserService.getCurrentUserId();
        String ipAddress = UserIpAddress.resolve(httpRequest, forwardedFor, realIp);

        // 1. Validate ALL geocoding settings together with full context
        List<UpdateSettingRequest> geocodingSettings = request.getSettings().stream()
                .filter(s -> s.getKey().startsWith("geocoding."))
                .collect(Collectors.toList());

        if (!geocodingSettings.isEmpty()) {
            String validationError = geocodingValidationService.validateGeocodingChanges(geocodingSettings);
            if (validationError != null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("message", validationError))
                        .build();
            }
        }

        // 2. Group settings by save order (same order as before for validation)
        List<UpdateSettingRequest> credentialSettings = request.getSettings().stream()
            .filter(s -> s.getKey().contains(".api-key") || s.getKey().contains(".access-token"))
            .collect(Collectors.toList());

        List<UpdateSettingRequest> enabledSettings = request.getSettings().stream()
            .filter(s -> s.getKey().contains(".enabled"))
            .collect(Collectors.toList());

        List<UpdateSettingRequest> providerSettings = request.getSettings().stream()
            .filter(s -> s.getKey().equals("geocoding.primary-provider") ||
                         s.getKey().equals("geocoding.fallback-provider"))
            .collect(Collectors.toList());

        List<UpdateSettingRequest> otherSettings = request.getSettings().stream()
            .filter(s -> !s.getKey().contains(".api-key") &&
                         !s.getKey().contains(".access-token") &&
                         !s.getKey().contains(".enabled") &&
                         !s.getKey().equals("geocoding.primary-provider") &&
                         !s.getKey().equals("geocoding.fallback-provider"))
            .collect(Collectors.toList());

        // 3. Save in correct order (within transaction)
        List<UpdateSettingRequest> orderedSettings = new ArrayList<>();
        orderedSettings.addAll(credentialSettings);
        orderedSettings.addAll(enabledSettings);
        orderedSettings.addAll(providerSettings);
        orderedSettings.addAll(otherSettings);

        for (UpdateSettingRequest settingUpdate : orderedSettings) {
            String oldValue = settingsService.getString(settingUpdate.getKey());
            settingsService.setValue(settingUpdate.getKey(), settingUpdate.getValue(), adminId);

            // Audit log each change
            auditLogService.logSettingChange(
                adminId,
                settingUpdate.getKey(),
                oldValue,
                settingUpdate.getValue(),
                ipAddress
            );
        }

        // 4. If we reach here, all saves succeeded (transaction commits)
        return Response.ok(Map.of("success", true, "updated", orderedSettings.size())).build();
    }
}
