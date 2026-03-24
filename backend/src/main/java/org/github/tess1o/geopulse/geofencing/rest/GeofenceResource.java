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
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventType;
import org.github.tess1o.geopulse.geofencing.service.AppriseNotificationService;
import org.github.tess1o.geopulse.geofencing.service.GeofenceEventService;
import org.github.tess1o.geopulse.geofencing.service.GeofenceRuleService;
import org.github.tess1o.geopulse.geofencing.service.NotificationTemplateService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private final GeofenceEventService eventService;
    private final NotificationTemplateService templateService;
    private final AppriseNotificationService appriseNotificationService;
    private final CurrentUserService currentUserService;

    @Inject
    public GeofenceResource(GeofenceRuleService ruleService,
                            GeofenceEventService eventService,
                            NotificationTemplateService templateService,
                            AppriseNotificationService appriseNotificationService,
                            CurrentUserService currentUserService) {
        this.ruleService = ruleService;
        this.eventService = eventService;
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
    @Path("/events")
    public Response getEvents(@QueryParam("page") @DefaultValue("0") int page,
                              @QueryParam("pageSize") @DefaultValue("25") int pageSize,
                              @QueryParam("sortBy") @DefaultValue("occurredAt") String sortBy,
                              @QueryParam("sortDir") @DefaultValue("desc") String sortDir,
                              @QueryParam("unreadOnly") @DefaultValue("false") boolean unreadOnly,
                              @QueryParam("dateFrom") String dateFromValue,
                              @QueryParam("dateTo") String dateToValue,
                              @QueryParam("subjectUserIds") String subjectUserIdsValue,
                              @QueryParam("eventTypes") String eventTypesValue) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            GeofenceEventQueryDto query = GeofenceEventQueryDto.builder()
                    .page(page)
                    .pageSize(pageSize)
                    .sortBy(sortBy)
                    .sortDir(sortDir)
                    .unreadOnly(unreadOnly)
                    .dateFrom(parseInstant(dateFromValue, "dateFrom"))
                    .dateTo(parseInstant(dateToValue, "dateTo"))
                    .subjectUserIds(parseUuidList(subjectUserIdsValue, "subjectUserIds"))
                    .eventTypes(parseEventTypes(eventTypesValue))
                    .build();
            GeofenceEventPageDto events = eventService.listEventsPage(userId, query);
            return Response.ok(ApiResponse.success(events)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to load geofence events", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to load geofence events"))
                    .build();
        }
    }

    @GET
    @Path("/events/unread-count")
    public Response getUnreadEventCount() {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            return Response.ok(ApiResponse.success(Map.of("count", eventService.countUnread(userId)))).build();
        } catch (Exception e) {
            log.error("Failed to load geofence unread event count", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to load geofence unread event count"))
                    .build();
        }
    }

    @POST
    @Path("/events/{eventId}/seen")
    public Response markEventSeen(@PathParam("eventId") Long eventId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            GeofenceEventDto updated = eventService.markSeen(userId, eventId);
            return Response.ok(ApiResponse.success(updated)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to mark geofence event {} as seen", eventId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to mark geofence event as seen"))
                    .build();
        }
    }

    @POST
    @Path("/events/seen-all")
    public Response markAllEventsSeen() {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            long updatedCount = eventService.markAllSeen(userId);
            return Response.ok(ApiResponse.success(Map.of("updatedCount", updatedCount))).build();
        } catch (Exception e) {
            log.error("Failed to mark geofence events as seen", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to mark geofence events as seen"))
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

    private Instant parseInstant(String value, String paramName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid " + paramName + " value. Expected ISO-8601 instant.");
        }
    }

    private List<String> parseCsvValues(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .distinct()
                .toList();
    }

    private List<UUID> parseUuidList(String value, String paramName) {
        List<String> rawValues = parseCsvValues(value);
        if (rawValues.isEmpty()) {
            return List.of();
        }

        try {
            return rawValues.stream().map(UUID::fromString).distinct().toList();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID in " + paramName + " filter.");
        }
    }

    private List<GeofenceEventType> parseEventTypes(String value) {
        List<String> rawValues = parseCsvValues(value);
        if (rawValues.isEmpty()) {
            return List.of();
        }

        try {
            return rawValues.stream()
                    .map(item -> GeofenceEventType.valueOf(item.toUpperCase(Locale.ROOT)))
                    .distinct()
                    .toList();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid eventTypes filter. Supported values: ENTER, LEAVE.");
        }
    }

}
