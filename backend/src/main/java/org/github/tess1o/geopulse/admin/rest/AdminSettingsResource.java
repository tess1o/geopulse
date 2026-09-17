package org.github.tess1o.geopulse.admin.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.github.tess1o.geopulse.admin.dto.MapMatchingProviderTestResponse;
import org.github.tess1o.geopulse.admin.dto.MapMatchingQueueRebuildResponse;
import org.github.tess1o.geopulse.admin.dto.PanoramaxTestResponse;
import org.github.tess1o.geopulse.admin.dto.SettingResetResponse;
import org.github.tess1o.geopulse.admin.dto.UpdateSettingRequest;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.SettingInfo;
import org.github.tess1o.geopulse.admin.model.TargetType;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.admin.service.GeocodingValidationService;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.admin.service.WeatherValidationService;
import org.github.tess1o.geopulse.auth.security.SecurityRoles;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.geofencing.client.AppriseClientResult;
import org.github.tess1o.geopulse.geofencing.model.dto.AppriseTestRequest;
import org.github.tess1o.geopulse.geofencing.service.AppriseNotificationService;
import org.github.tess1o.geopulse.mapmatching.service.MapMatchingConfiguration;
import org.github.tess1o.geopulse.mapmatching.service.MapMatchingWorker;
import org.github.tess1o.geopulse.mapmatching.dto.MapMatchingAdminStatusDTO;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationHealthStatus;
import org.github.tess1o.geopulse.integration.model.ExternalIntegrationType;
import org.github.tess1o.geopulse.integration.service.ExternalIntegrationHealthService;
import org.github.tess1o.geopulse.shared.api.UserIpAddress;
import org.github.tess1o.geopulse.weather.dto.WeatherTestResponse;
import org.github.tess1o.geopulse.weather.service.WeatherService;
import org.github.tess1o.geopulse.geofencing.model.dto.AppriseTestResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

/**
 * REST resource for admin settings management.
 */
@Path("/api/admin/settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = "Admin: System Settings", description = "View, update, reset, and test system-wide settings.")
public class AdminSettingsResource {

    @Context
    HttpServerRequest httpRequest;

    @Inject
    SystemSettingsService settingsService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AuditLogService auditLogService;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    GeocodingValidationService geocodingValidationService;

    @Inject
    WeatherValidationService weatherValidationService;

    @Inject
    AppriseNotificationService appriseNotificationService;

    @Inject
    WeatherService weatherService;

    @Inject
    MapMatchingConfiguration mapMatchingConfiguration;

    @Inject
    MapMatchingWorker mapMatchingWorker;

    @Inject
    ExternalIntegrationHealthService integrationHealthService;

    /**
     * Get all settings grouped by category.
     */
    @GET
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.DEMO_ADMIN_READ})
    public Map<String, List<SettingInfo>> getAllSettings() {
        return settingsService.getAllSettings();
    }

    /**
     * Get settings for a specific category.
     */
    @GET
    @Path("/{category}")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.DEMO_ADMIN_READ})
    public List<SettingInfo> getSettingsByCategory(@PathParam("category") String category) {
        return settingsService.getSettingsByCategory(category);
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
    @RolesAllowed(SecurityRoles.ADMIN)
    public void updateSetting(
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

    }

    /**
     * Reset a setting to default (delete from DB).
     */
    @DELETE
    @Path("/{key}")
    @RolesAllowed(SecurityRoles.ADMIN)
    public SettingResetResponse resetSetting(
            @PathParam("key") String key,
            @HeaderParam("X-Forwarded-For") String forwardedFor,
            @HeaderParam("X-Real-IP") String realIp) {

        UUID adminId = currentUserService.getCurrentUserId();
        String oldValue = settingsService.getString(key);

        settingsService.resetToDefault(key);

        // Audit log
        String ipAddress = UserIpAddress.resolve(httpRequest, forwardedFor, realIp);
        auditLogService.logSettingReset(adminId, key, oldValue, ipAddress);

        return new SettingResetResponse(settingsService.getDefaultValue(key));
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
    @RolesAllowed(SecurityRoles.ADMIN)
    public void bulkUpdateSettings(
            BulkUpdateRequest request,
            @HeaderParam("X-Forwarded-For") String forwardedFor,
            @HeaderParam("X-Real-IP") String realIp) {

        UUID adminId = currentUserService.getCurrentUserId();
        String ipAddress = UserIpAddress.resolve(httpRequest, forwardedFor, realIp);

        // 1. Validate ALL provider settings together with full context
        List<UpdateSettingRequest> geocodingSettings = request.getSettings().stream()
                .filter(s -> s.getKey().startsWith("geocoding."))
                .collect(Collectors.toList());

        if (!geocodingSettings.isEmpty()) {
            String validationError = geocodingValidationService.validateGeocodingChanges(geocodingSettings);
            if (validationError != null) {
                throw problem(INVALID_ADMIN_SETTINGS, validationError);
            }
        }

        List<UpdateSettingRequest> weatherSettings = request.getSettings().stream()
                .filter(s -> s.getKey().startsWith("weather."))
                .collect(Collectors.toList());

        if (!weatherSettings.isEmpty()) {
            String validationError = weatherValidationService.validateWeatherChanges(weatherSettings);
            if (validationError != null) {
                throw problem(INVALID_ADMIN_SETTINGS, validationError);
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
                         s.getKey().equals("geocoding.fallback-provider") ||
                         s.getKey().equals("weather.primary-provider") ||
                         s.getKey().equals("weather.secondary-provider"))
            .collect(Collectors.toList());

        List<UpdateSettingRequest> otherSettings = request.getSettings().stream()
            .filter(s -> !s.getKey().contains(".api-key") &&
                         !s.getKey().contains(".access-token") &&
                         !s.getKey().contains(".enabled") &&
                         !s.getKey().equals("geocoding.primary-provider") &&
                         !s.getKey().equals("geocoding.fallback-provider") &&
                         !s.getKey().equals("weather.primary-provider") &&
                         !s.getKey().equals("weather.secondary-provider"))
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

        // Transaction commits when this method returns.
    }

    /**
     * Test Apprise connectivity using current system settings.
     */
    @POST
    @Path("/system/notifications/apprise/test")
    @RolesAllowed(SecurityRoles.ADMIN)
    public AppriseTestResponse testAppriseConnection(AppriseTestRequest request) {
        AppriseClientResult result = appriseNotificationService.testConnection(request);
        if (result == null) {
            return new AppriseTestResponse(false, 0, "Apprise test failed: no response from client");
        }

        String message = result.getMessage() != null && !result.getMessage().isBlank()
                ? result.getMessage()
                : (result.isSuccess() ? "Apprise endpoint is reachable" : "Apprise test failed");

        return new AppriseTestResponse(result.isSuccess(), result.getStatusCode(), message);
    }

    @POST
    @Path("/weather/test")
    @RolesAllowed(SecurityRoles.ADMIN)
    public WeatherTestResponse testWeatherConnection() {
        return weatherService.testProviderConnection();
    }

    @POST
    @Path("/panoramax/test")
    @RolesAllowed(SecurityRoles.ADMIN)
    public PanoramaxTestResponse testPanoramaxConnection() {
        String endpoint = settingsService.getString("panoramax.endpoint").trim();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            boolean hasVectorTiles = response.statusCode() >= 200 && response.statusCode() < 300
                    && hasVectorTiles(objectMapper.readTree(response.body()));
            return new PanoramaxTestResponse(hasVectorTiles, endpoint,
                    hasVectorTiles ? null : "No Panoramax vector-tile link found");
        } catch (Exception exception) {
            return new PanoramaxTestResponse(false, endpoint,
                    "Could not reach Panoramax endpoint: " + exception.getMessage());
        }
    }

    static boolean hasVectorTiles(JsonNode catalog) {
        JsonNode links = catalog.path("links");
        if (!links.isArray()) {
            return false;
        }
        for (JsonNode link : links) {
            if ("xyz".equals(link.path("rel").asText())) {
                return true;
            }
        }
        return false;
    }

    @POST
    @Path("/map-matching/valhalla/test")
    @RolesAllowed(SecurityRoles.ADMIN)
    public MapMatchingProviderTestResponse testValhallaConnection() {
        if (!mapMatchingConfiguration.valhallaConfigured()) {
            return new MapMatchingProviderTestResponse(
                    false, 0, "valhalla", null, "Valhalla base URL is not configured");
        }

        String baseUrl = mapMatchingConfiguration.valhallaBaseUrl();
        URI statusUri = URI.create(baseUrl.endsWith("/") ? baseUrl + "status" : baseUrl + "/status");
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, mapMatchingConfiguration.getConnectTimeoutSeconds())))
                .build()) {
            HttpRequest request = HttpRequest.newBuilder(statusUri)
                    .timeout(Duration.ofSeconds(Math.max(1, mapMatchingConfiguration.getReadTimeoutSeconds())))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String detail = response.statusCode() >= 200 && response.statusCode() < 300
                    ? "Valhalla endpoint is reachable"
                    : limitErrorBody(response.body());
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;

            if (success) {
                integrationHealthService.recordSuccess(ExternalIntegrationType.MAP_MATCHING, "valhalla");
                return new MapMatchingProviderTestResponse(
                        true, response.statusCode(), "valhalla", statusUri.toString(), null);
            }
            integrationHealthService.recordFailure(ExternalIntegrationType.MAP_MATCHING, "valhalla",
                    ExternalIntegrationHealthStatus.PROVIDER_UNAVAILABLE, "HTTP_" + response.statusCode(),
                    detail, null, null);
            return new MapMatchingProviderTestResponse(
                    false, response.statusCode(), "valhalla", statusUri.toString(), detail);
        } catch (Exception e) {
            String detail = e.getMessage() == null ? "Valhalla connection failed" : e.getMessage();
            integrationHealthService.recordFailure(ExternalIntegrationType.MAP_MATCHING, "valhalla",
                    ExternalIntegrationHealthStatus.PROVIDER_UNAVAILABLE, e.getClass().getSimpleName(),
                    e.getMessage(), null, null);
            return new MapMatchingProviderTestResponse(false, 0, "valhalla", statusUri.toString(), detail);
        }
    }

    @GET
    @Path("/map-matching/status")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.DEMO_ADMIN_READ})
    public MapMatchingAdminStatusDTO mapMatchingStatus() {
        return mapMatchingWorker.status();
    }

    @POST
    @Path("/map-matching/historical/rebuild")
    @RolesAllowed(SecurityRoles.ADMIN)
    public MapMatchingQueueRebuildResponse rebuildMapMatchingHistoricalQueue(
            @HeaderParam("X-Forwarded-For") String forwardedFor,
            @HeaderParam("X-Real-IP") String realIp) {
        if (!mapMatchingConfiguration.isEnabled()) {
            throw problem(MAP_MATCHING_DISABLED, "Map matching is disabled");
        }
        if (!mapMatchingConfiguration.backfillEnabled()) {
            throw problem(MAP_MATCHING_BACKFILL_DISABLED, "Historical backfill is disabled");
        }
        if (!"valhalla".equals(mapMatchingConfiguration.provider()) || !mapMatchingConfiguration.valhallaConfigured()) {
            throw problem(MAP_MATCHING_PROVIDER_NOT_CONFIGURED, "Valhalla is not configured");
        }

        long queuedUsers = mapMatchingWorker.rebuildHistoricalQueue();
        UUID adminId = currentUserService.getCurrentUserId();
        String ipAddress = UserIpAddress.resolve(httpRequest, forwardedFor, realIp);
        auditLogService.logAction(
                adminId,
                ActionType.MAP_MATCHING_HISTORICAL_REBUILD,
                TargetType.SETTING,
                "map-matching.historical-rebuild",
                Map.of("queuedUsers", queuedUsers),
                ipAddress
        );

        return new MapMatchingQueueRebuildResponse(queuedUsers);
    }

    private String limitErrorBody(String body) {
        if (body == null || body.isBlank()) {
            return "Valhalla endpoint returned an error";
        }
        String trimmed = body.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }
}
