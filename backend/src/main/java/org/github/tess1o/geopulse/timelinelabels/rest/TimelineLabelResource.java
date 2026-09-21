package org.github.tess1o.geopulse.timelinelabels.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.timelinelabels.model.dto.CreateTimelineLabelDto;
import org.github.tess1o.geopulse.timelinelabels.model.dto.TimelineLabelDto;
import org.github.tess1o.geopulse.timelinelabels.model.dto.UpdateTimelineLabelDto;
import org.github.tess1o.geopulse.timelinelabels.service.TimelineLabelService;
import org.jboss.resteasy.reactive.RestResponse;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TIMELINE_LABEL;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TIMELINE_LABEL_DELETE_MODE;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TIMELINE_LABEL_RANGE;

@Path("/timeline-labels")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: Timeline Labels", description = "Manage timeline labels and overlap checks.")
public class TimelineLabelResource {

    private final TimelineLabelService service;
    private final CurrentUserService currentUserService;

    @Inject
    public TimelineLabelResource(TimelineLabelService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GET
    public List<TimelineLabelDto> getTimelineLabels(@QueryParam("from") String from,
                                                    @QueryParam("to") String to) {
        UUID userId = currentUserService.getCurrentUserId();
        if (from == null && to == null) {
            return service.getTimelineLabels(userId);
        }
        if (from == null || to == null) {
            throw new GeoPulseException(INVALID_TIMELINE_LABEL_RANGE, "from and to must be provided together");
        }
        try {
            Instant start = Instant.parse(from);
            Instant end = Instant.parse(to);
            if (start.isAfter(end)) {
                throw new GeoPulseException(INVALID_TIMELINE_LABEL_RANGE, "from must not be after to");
            }
            return service.getTimelineLabelsForTimeRange(userId, start, end);
        } catch (DateTimeException exception) {
            throw new GeoPulseException(INVALID_TIMELINE_LABEL_RANGE, "from and to must be valid ISO-8601 instants", exception);
        }
    }

    @GET
    @Path("/active")
    @APIResponse(responseCode = "204", description = "No active timeline label")
    public RestResponse<TimelineLabelDto> getActiveLabel() {
        return service.getActiveLabel(currentUserService.getCurrentUserId())
                .map(RestResponse::ok)
                .orElseGet(RestResponse::noContent);
    }

    /**
     * Reports the labels a proposed range would overlap. This is a dry run of the
     * create/update payload, so it takes the same range names the DTOs use.
     */
    @GET
    @Path("/check-overlaps")
    public List<TimelineLabelDto> checkOverlaps(@QueryParam("startTime") String startTime,
                                                @QueryParam("endTime") String endTime,
                                                @QueryParam("excludeId") Long excludeId) {
        if (startTime == null || endTime == null) {
            throw new GeoPulseException(INVALID_TIMELINE_LABEL_RANGE, "startTime and endTime are required");
        }
        try {
            Instant start = Instant.parse(startTime);
            Instant end = Instant.parse(endTime);
            if (!end.isAfter(start)) {
                throw new GeoPulseException(INVALID_TIMELINE_LABEL_RANGE, "endTime must be after startTime");
            }
            return service.checkOverlaps(currentUserService.getCurrentUserId(), start, end, excludeId);
        } catch (DateTimeException exception) {
            throw new GeoPulseException(INVALID_TIMELINE_LABEL_RANGE, "startTime and endTime must be valid ISO-8601 timestamps", exception);
        }
    }

    @POST
    @APIResponse(responseCode = "201", description = "Timeline label created")
    public RestResponse<TimelineLabelDto> createTimelineLabel(@NotNull @Valid CreateTimelineLabelDto request) {
        try {
            TimelineLabelDto created = service.createTimelineLabel(currentUserService.getCurrentUserId(), request);
            return RestResponse.status(Response.Status.CREATED, created);
        } catch (IllegalArgumentException exception) {
            throw new GeoPulseException(INVALID_TIMELINE_LABEL, INVALID_TIMELINE_LABEL.title(), exception);
        }
    }

    @PUT
    @Path("/{id}")
    public TimelineLabelDto updateTimelineLabel(@PathParam("id") Long id,
                                                @NotNull @Valid UpdateTimelineLabelDto request) {
        try {
            return service.updateTimelineLabel(currentUserService.getCurrentUserId(), id, request);
        } catch (IllegalArgumentException exception) {
            throw new GeoPulseException(INVALID_TIMELINE_LABEL, INVALID_TIMELINE_LABEL.title(), exception);
        }
    }

    @DELETE
    @Path("/{id}")
    @APIResponse(responseCode = "204", description = "Timeline label deleted")
    public RestResponse<Void> deleteTimelineLabel(@PathParam("id") Long id,
                                                  @QueryParam("mode") @DefaultValue("unlink_only") String mode) {
        if (!"unlink_only".equalsIgnoreCase(mode) && !"delete_both".equalsIgnoreCase(mode)) {
            throw new GeoPulseException(INVALID_TIMELINE_LABEL_DELETE_MODE,
                    "Invalid delete mode. Supported values: unlink_only, delete_both");
        }
        try {
            service.deleteTimelineLabel(currentUserService.getCurrentUserId(), id,
                    "delete_both".equalsIgnoreCase(mode));
            return RestResponse.noContent();
        } catch (IllegalArgumentException exception) {
            throw new GeoPulseException(INVALID_TIMELINE_LABEL, INVALID_TIMELINE_LABEL.title(), exception);
        }
    }
}
