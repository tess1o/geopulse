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
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TIMELINE_LABEL;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TIMELINE_LABEL_DELETE_MODE;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TIMELINE_LABEL_RANGE;

@Path("/timeline-labels")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = ApiTags.TIMELINE_LABELS)
public class TimelineLabelResource {

    private final TimelineLabelService service;
    private final CurrentUserService currentUserService;

    @Inject
    public TimelineLabelResource(TimelineLabelService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GET
    @Operation(summary = "List timeline labels",
            description = "Returns all timeline labels of the signed-in user, or only those that overlap a time "
                    + "range when `from` and `to` are given (both are required together).")
    public List<TimelineLabelDto> getTimelineLabels(
            @Parameter(description = "Start of the time range, as an ISO-8601 instant.",
                    example = "2025-06-01T00:00:00Z")
            @QueryParam("from") String from,
            @Parameter(description = "End of the time range, as an ISO-8601 instant.",
                    example = "2025-06-30T23:59:59Z")
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
    @Operation(summary = "Get the active label",
            description = "Returns the label that the OwnTracks app is currently recording (created from an "
                    + "OwnTracks tag), or `204 No Content` when there is none. Active labels cannot be edited or "
                    + "deleted until they are completed.")
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
    @Operation(summary = "Find overlapping labels",
            description = "Returns the existing labels that a proposed time range would overlap. Overlaps are "
                    + "allowed; use this to warn before creating or moving a label.")
    public List<TimelineLabelDto> checkOverlaps(
            @Parameter(description = "Start of the proposed range, as an ISO-8601 instant.",
                    example = "2025-06-01T00:00:00Z")
            @QueryParam("startTime") String startTime,
            @Parameter(description = "End of the proposed range, as an ISO-8601 instant.",
                    example = "2025-06-07T23:59:59Z")
            @QueryParam("endTime") String endTime,
            @Parameter(description = "Label ID to ignore, for example the label being edited.")
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
    @Operation(summary = "Create a timeline label",
            description = "Creates a named, colored label for a time range. Labels marked as presets appear as quick "
                    + "date-range choices in the web app.")
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
    @Operation(summary = "Update a timeline label",
            description = "Changes the name, time range, color, or preset flag of a label. If a trip was created "
                    + "from the label, the trip is updated to match.")
    public TimelineLabelDto updateTimelineLabel(
            @Parameter(description = "Timeline label ID.")
            @PathParam("id") Long id,
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
    @Operation(summary = "Delete a timeline label",
            description = "Deletes a label. If a trip was created from it, `mode` decides what happens to the trip.")
    public RestResponse<Void> deleteTimelineLabel(
            @Parameter(description = "Timeline label ID.")
            @PathParam("id") Long id,
            @Parameter(description = "`unlink_only` (default) keeps the linked trip and removes the link; "
                    + "`delete_both` deletes the trip too.")
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
