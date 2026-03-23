package org.github.tess1o.geopulse.geofencing.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.geofencing.client.AppriseClientResult;
import org.github.tess1o.geopulse.geofencing.model.dto.*;
import org.github.tess1o.geopulse.geofencing.service.AppriseNotificationService;
import org.github.tess1o.geopulse.geofencing.service.GeofenceRuleService;
import org.github.tess1o.geopulse.geofencing.service.NotificationTemplateService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/geofences")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Slf4j
public class GeofenceResource {

    private final GeofenceRuleService ruleService;
    private final NotificationTemplateService templateService;
    private final AppriseNotificationService appriseNotificationService;
    private final CurrentUserService currentUserService;

    @Inject
    public GeofenceResource(GeofenceRuleService ruleService,
                            NotificationTemplateService templateService,
                            AppriseNotificationService appriseNotificationService,
                            CurrentUserService currentUserService) {
        this.ruleService = ruleService;
        this.templateService = templateService;
        this.appriseNotificationService = appriseNotificationService;
        this.currentUserService = currentUserService;
    }

    @GET
    @Path("/rules")
    public Response getRules() {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            List<GeofenceRuleDto> rules = ruleService.listRules(userId);
            return Response.ok(ApiResponse.success(rules)).build();
        } catch (Exception e) {
            log.error("Failed to load geofence rules", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to load geofence rules"))
                    .build();
        }
    }

    @POST
    @Path("/rules")
    public Response createRule(@Valid CreateGeofenceRuleRequest request) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            GeofenceRuleDto created = ruleService.createRule(userId, request);
            return Response.status(Response.Status.CREATED)
                    .entity(ApiResponse.success(created))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to create geofence rule", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to create geofence rule"))
                    .build();
        }
    }

    @PATCH
    @Path("/rules/{ruleId}")
    public Response updateRule(@PathParam("ruleId") Long ruleId, @Valid UpdateGeofenceRuleRequest request) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            GeofenceRuleDto updated = ruleService.updateRule(userId, ruleId, request);
            return Response.ok(ApiResponse.success(updated)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to update geofence rule {}", ruleId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to update geofence rule"))
                    .build();
        }
    }

    @DELETE
    @Path("/rules/{ruleId}")
    public Response deleteRule(@PathParam("ruleId") Long ruleId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            ruleService.deleteRule(userId, ruleId);
            return Response.ok(ApiResponse.success("Geofence rule deleted")).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to delete geofence rule {}", ruleId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to delete geofence rule"))
                    .build();
        }
    }

    @GET
    @Path("/templates")
    public Response getTemplates() {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            List<NotificationTemplateDto> templates = templateService.listTemplates(userId);
            return Response.ok(ApiResponse.success(templates)).build();
        } catch (Exception e) {
            log.error("Failed to load notification templates", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to load notification templates"))
                    .build();
        }
    }

    @GET
    @Path("/templates/capabilities")
    public Response getTemplateCapabilities() {
        try {
            TemplateDeliveryCapabilitiesDto capabilities = TemplateDeliveryCapabilitiesDto.builder()
                    .appriseEnabled(appriseNotificationService.isEnabled())
                    .appriseConfigured(appriseNotificationService.isConfigured())
                    .build();
            return Response.ok(ApiResponse.success(capabilities)).build();
        } catch (Exception e) {
            log.error("Failed to load template delivery capabilities", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to load template delivery capabilities"))
                    .build();
        }
    }

    @POST
    @Path("/templates/test-connection")
    public Response testTemplateConnection(AppriseTestRequest request) {
        try {
            AppriseClientResult result = appriseNotificationService.testConnection(request);
            if (result == null) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ApiResponse.error("Apprise test failed: no response from client"))
                        .build();
            }

            String message = result.getMessage() != null && !result.getMessage().isBlank()
                    ? result.getMessage()
                    : (result.isSuccess() ? "Apprise endpoint is reachable" : "Apprise test failed");

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("statusCode", result.getStatusCode());
            details.put("success", result.isSuccess());
            details.put("message", message);

            if (result.isSuccess()) {
                return Response.ok(ApiResponse.success(details)).build();
            }
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(message, details))
                    .build();
        } catch (Exception e) {
            log.error("Failed to test template connection", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to test template connection"))
                    .build();
        }
    }

    @POST
    @Path("/templates")
    public Response createTemplate(@Valid CreateNotificationTemplateRequest request) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            NotificationTemplateDto created = templateService.createTemplate(userId, request);
            return Response.status(Response.Status.CREATED)
                    .entity(ApiResponse.success(created))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to create notification template", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to create notification template"))
                    .build();
        }
    }

    @PATCH
    @Path("/templates/{templateId}")
    public Response updateTemplate(@PathParam("templateId") Long templateId,
                                   @Valid UpdateNotificationTemplateRequest request) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            NotificationTemplateDto updated = templateService.updateTemplate(userId, templateId, request);
            return Response.ok(ApiResponse.success(updated)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to update notification template {}", templateId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to update notification template"))
                    .build();
        }
    }

    @DELETE
    @Path("/templates/{templateId}")
    public Response deleteTemplate(@PathParam("templateId") Long templateId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            templateService.deleteTemplate(userId, templateId);
            return Response.ok(ApiResponse.success("Notification template deleted")).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to delete notification template {}", templateId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to delete notification template"))
                    .build();
        }
    }

}
