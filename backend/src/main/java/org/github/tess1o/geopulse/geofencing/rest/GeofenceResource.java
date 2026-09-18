package org.github.tess1o.geopulse.geofencing.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.geofencing.client.AppriseClientResult;
import org.github.tess1o.geopulse.geofencing.model.dto.AppriseTestRequest;
import org.github.tess1o.geopulse.geofencing.model.dto.AppriseTestResponse;
import org.github.tess1o.geopulse.geofencing.model.dto.CreateGeofenceRuleRequest;
import org.github.tess1o.geopulse.geofencing.model.dto.CreateNotificationTemplateRequest;
import org.github.tess1o.geopulse.geofencing.model.dto.GeofenceEventDto;
import org.github.tess1o.geopulse.shared.api.PageResponse;
import org.github.tess1o.geopulse.geofencing.model.dto.GeofenceEventQueryDto;
import org.github.tess1o.geopulse.geofencing.model.dto.GeofenceRuleDto;
import org.github.tess1o.geopulse.geofencing.model.dto.NotificationTemplateDto;
import org.github.tess1o.geopulse.geofencing.model.dto.TemplateDeliveryCapabilitiesDto;
import org.github.tess1o.geopulse.geofencing.model.dto.UpdateGeofenceRuleRequest;
import org.github.tess1o.geopulse.geofencing.model.dto.UpdateNotificationTemplateRequest;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventType;
import org.github.tess1o.geopulse.geofencing.service.AppriseNotificationService;
import org.github.tess1o.geopulse.geofencing.service.GeofenceEventService;
import org.github.tess1o.geopulse.geofencing.service.GeofenceRuleService;
import org.github.tess1o.geopulse.geofencing.service.NotificationTemplateService;
import org.github.tess1o.geopulse.shared.api.CountResponse;
import org.github.tess1o.geopulse.shared.api.UpdatedCountResponse;
import org.jboss.resteasy.reactive.RestResponse;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.APPRISE_TEST_FAILED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.GEOFENCE_EVENT_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.GEOFENCE_RULE_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_GEOFENCE_QUERY;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_GEOFENCE_RULE;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_NOTIFICATION_TEMPLATE;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.NOTIFICATION_TEMPLATE_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/geofences")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: Geofences", description = "Manage geofence rules, events, templates, and notification tests.")
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
    public List<GeofenceRuleDto> getRules() {
        return ruleService.listRules(currentUserService.getCurrentUserId());
    }

    @POST
    @Path("/rules")
    @APIResponse(responseCode = "201", description = "Geofence rule created")
    public RestResponse<GeofenceRuleDto> createRule(@NotNull @Valid CreateGeofenceRuleRequest request) {
        try {
            GeofenceRuleDto created = ruleService.createRule(currentUserService.getCurrentUserId(), request);
            return RestResponse.status(Response.Status.CREATED, created);
        } catch (IllegalArgumentException exception) {
            throw problem(INVALID_GEOFENCE_RULE, exception.getMessage());
        }
    }

    @PATCH
    @Path("/rules/{ruleId}")
    public GeofenceRuleDto updateRule(@PathParam("ruleId") Long ruleId,
                                      @NotNull @Valid UpdateGeofenceRuleRequest request) {
        try {
            return ruleService.updateRule(currentUserService.getCurrentUserId(), ruleId, request);
        } catch (NoSuchElementException exception) {
            throw problem(GEOFENCE_RULE_NOT_FOUND, exception.getMessage(), Map.of("ruleId", ruleId));
        } catch (IllegalArgumentException exception) {
            throw problem(INVALID_GEOFENCE_RULE, exception.getMessage(), Map.of("ruleId", ruleId));
        }
    }

    @DELETE
    @Path("/rules/{ruleId}")
    @APIResponse(responseCode = "204", description = "Geofence rule deleted")
    public RestResponse<Void> deleteRule(@PathParam("ruleId") Long ruleId) {
        try {
            ruleService.deleteRule(currentUserService.getCurrentUserId(), ruleId);
            return RestResponse.noContent();
        } catch (NoSuchElementException exception) {
            throw problem(GEOFENCE_RULE_NOT_FOUND, exception.getMessage(), Map.of("ruleId", ruleId));
        }
    }

    @GET
    @Path("/events")
    public PageResponse<GeofenceEventDto> getEvents(@QueryParam("page") @DefaultValue("0") int page,
                                          @QueryParam("size") @DefaultValue("25") int pageSize,
                                          @QueryParam("sortBy") @DefaultValue("occurredAt") String sortBy,
                                          @QueryParam("sortDirection") @DefaultValue("desc") String sortDir,
                                          @QueryParam("unreadOnly") @DefaultValue("false") boolean unreadOnly,
                                          @QueryParam("from") String dateFromValue,
                                          @QueryParam("to") String dateToValue,
                                          @QueryParam("subjectUserIds") String subjectUserIdsValue,
                                          @QueryParam("eventTypes") String eventTypesValue) {
        try {
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
            return eventService.listEventsPage(currentUserService.getCurrentUserId(), query);
        } catch (IllegalArgumentException exception) {
            throw problem(INVALID_GEOFENCE_QUERY, exception.getMessage());
        }
    }

    @GET
    @Path("/events/unread-count")
    public CountResponse getUnreadEventCount() {
        return new CountResponse(eventService.countUnread(currentUserService.getCurrentUserId()));
    }

    @PATCH
    @Path("/events/{eventId}/read-status")
    public GeofenceEventDto markEventSeen(@PathParam("eventId") Long eventId) {
        try {
            return eventService.markSeen(currentUserService.getCurrentUserId(), eventId);
        } catch (NoSuchElementException exception) {
            throw problem(GEOFENCE_EVENT_NOT_FOUND, exception.getMessage(), Map.of("eventId", eventId));
        }
    }

    @PATCH
    @Path("/events/read-status")
    public UpdatedCountResponse markAllEventsSeen() {
        return new UpdatedCountResponse(eventService.markAllSeen(currentUserService.getCurrentUserId()));
    }

    @GET
    @Path("/templates")
    public List<NotificationTemplateDto> getTemplates() {
        return templateService.listTemplates(currentUserService.getCurrentUserId());
    }

    @GET
    @Path("/templates/capabilities")
    public TemplateDeliveryCapabilitiesDto getTemplateCapabilities() {
        return TemplateDeliveryCapabilitiesDto.builder()
                .appriseEnabled(appriseNotificationService.isEnabled())
                .appriseConfigured(appriseNotificationService.isConfigured())
                .build();
    }

    @POST
    @Path("/templates/connection-tests")
    public AppriseTestResponse testTemplateConnection(@NotNull @Valid AppriseTestRequest request) {
        AppriseClientResult result = appriseNotificationService.testConnection(request);
        if (result == null) {
            throw problem(APPRISE_TEST_FAILED, "Apprise test returned no response");
        }
        return new AppriseTestResponse(result.isSuccess(), result.getStatusCode(), result.getMessage());
    }

    @POST
    @Path("/templates")
    @APIResponse(responseCode = "201", description = "Notification template created")
    public RestResponse<NotificationTemplateDto> createTemplate(
            @NotNull @Valid CreateNotificationTemplateRequest request) {
        try {
            NotificationTemplateDto created = templateService.createTemplate(currentUserService.getCurrentUserId(), request);
            return RestResponse.status(Response.Status.CREATED, created);
        } catch (IllegalArgumentException exception) {
            throw problem(INVALID_NOTIFICATION_TEMPLATE, exception.getMessage());
        }
    }

    @PATCH
    @Path("/templates/{templateId}")
    public NotificationTemplateDto updateTemplate(@PathParam("templateId") Long templateId,
                                                   @NotNull @Valid UpdateNotificationTemplateRequest request) {
        try {
            return templateService.updateTemplate(currentUserService.getCurrentUserId(), templateId, request);
        } catch (NoSuchElementException exception) {
            throw problem(NOTIFICATION_TEMPLATE_NOT_FOUND, exception.getMessage(), Map.of("templateId", templateId));
        } catch (IllegalArgumentException exception) {
            throw problem(INVALID_NOTIFICATION_TEMPLATE, exception.getMessage(), Map.of("templateId", templateId));
        }
    }

    @DELETE
    @Path("/templates/{templateId}")
    @APIResponse(responseCode = "204", description = "Notification template deleted")
    public RestResponse<Void> deleteTemplate(@PathParam("templateId") Long templateId) {
        try {
            templateService.deleteTemplate(currentUserService.getCurrentUserId(), templateId);
            return RestResponse.noContent();
        } catch (NoSuchElementException exception) {
            throw problem(NOTIFICATION_TEMPLATE_NOT_FOUND, exception.getMessage(), Map.of("templateId", templateId));
        }
    }

    private Instant parseInstant(String value, String paramName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException exception) {
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
        } catch (IllegalArgumentException exception) {
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
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid eventTypes filter. Supported values: ENTER, LEAVE.");
        }
    }
}
