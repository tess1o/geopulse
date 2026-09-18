package org.github.tess1o.geopulse.periods.rest;

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
import org.github.tess1o.geopulse.periods.model.dto.CreatePeriodTagDto;
import org.github.tess1o.geopulse.periods.model.dto.PeriodTagDto;
import org.github.tess1o.geopulse.periods.model.dto.UpdatePeriodTagDto;
import org.github.tess1o.geopulse.periods.service.PeriodTagService;
import org.jboss.resteasy.reactive.RestResponse;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_PERIOD_DELETE_MODE;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_PERIOD_RANGE;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_PERIOD_TAG;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/period-tags")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: Period Tags", description = "Manage period tags and overlap checks.")
public class PeriodTagResource {

    private final PeriodTagService service;
    private final CurrentUserService currentUserService;

    @Inject
    public PeriodTagResource(PeriodTagService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GET
    public List<PeriodTagDto> getPeriodTags(@QueryParam("from") String from,
                                            @QueryParam("to") String to) {
        UUID userId = currentUserService.getCurrentUserId();
        if (from == null && to == null) {
            return service.getPeriodTags(userId);
        }
        try {
            Instant start = Instant.parse(from);
            Instant end = Instant.parse(to);
            if (start.isAfter(end)) {
                throw problem(INVALID_PERIOD_RANGE, "from must not be after to");
            }
            return service.getPeriodTagsForTimeRange(userId, start, end);
        } catch (NullPointerException | DateTimeException exception) {
            throw problem(INVALID_PERIOD_RANGE, "from and to must be valid ISO-8601 instants");
        }
    }

    @GET
    @Path("/active")
    @APIResponse(responseCode = "204", description = "No active period tag")
    public RestResponse<PeriodTagDto> getActiveTag() {
        return service.getActiveTag(currentUserService.getCurrentUserId())
                .map(RestResponse::ok)
                .orElseGet(RestResponse::noContent);
    }

    @GET
    @Path("/check-overlaps")
    public List<PeriodTagDto> checkOverlaps(@QueryParam("from") String startTime,
                                            @QueryParam("to") String endTime,
                                            @QueryParam("excludeId") Long excludeId) {
        if (startTime == null || endTime == null) {
            throw problem(INVALID_PERIOD_RANGE, "startTime and endTime are required");
        }
        try {
            Instant start = Instant.parse(startTime);
            Instant end = Instant.parse(endTime);
            if (!end.isAfter(start)) {
                throw problem(INVALID_PERIOD_RANGE, "endTime must be after startTime");
            }
            return service.checkOverlaps(currentUserService.getCurrentUserId(), start, end, excludeId);
        } catch (DateTimeException exception) {
            throw problem(INVALID_PERIOD_RANGE, "startTime and endTime must be valid ISO-8601 timestamps");
        }
    }

    @POST
    @APIResponse(responseCode = "201", description = "Period tag created")
    public RestResponse<PeriodTagDto> createPeriodTag(@NotNull @Valid CreatePeriodTagDto request) {
        try {
            PeriodTagDto created = service.createPeriodTag(currentUserService.getCurrentUserId(), request);
            return RestResponse.status(Response.Status.CREATED, created);
        } catch (IllegalArgumentException exception) {
            throw problem(INVALID_PERIOD_TAG, exception.getMessage());
        }
    }

    @PUT
    @Path("/{id}")
    public PeriodTagDto updatePeriodTag(@PathParam("id") Long id,
                                        @NotNull @Valid UpdatePeriodTagDto request) {
        try {
            return service.updatePeriodTag(currentUserService.getCurrentUserId(), id, request);
        } catch (IllegalArgumentException exception) {
            throw problem(INVALID_PERIOD_TAG, exception.getMessage());
        }
    }

    @DELETE
    @Path("/{id}")
    @APIResponse(responseCode = "204", description = "Period tag deleted")
    public RestResponse<Void> deletePeriodTag(@PathParam("id") Long id,
                                              @QueryParam("mode") @DefaultValue("unlink_only") String mode) {
        if (!"unlink_only".equalsIgnoreCase(mode) && !"delete_both".equalsIgnoreCase(mode)) {
            throw problem(INVALID_PERIOD_DELETE_MODE,
                    "Invalid delete mode. Supported values: unlink_only, delete_both");
        }
        try {
            service.deletePeriodTag(currentUserService.getCurrentUserId(), id,
                    "delete_both".equalsIgnoreCase(mode));
            return RestResponse.noContent();
        } catch (IllegalArgumentException exception) {
            throw problem(INVALID_PERIOD_TAG, exception.getMessage());
        }
    }
}
