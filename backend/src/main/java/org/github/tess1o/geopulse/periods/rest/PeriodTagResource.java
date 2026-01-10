package org.github.tess1o.geopulse.periods.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.periods.model.dto.*;
import org.github.tess1o.geopulse.periods.service.PeriodTagService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Path("/api/period-tags")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Slf4j
public class PeriodTagResource {

    private final PeriodTagService service;
    private final CurrentUserService currentUserService;

    @Inject
    public PeriodTagResource(PeriodTagService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GET
    @Path("")
    public Response getPeriodTags(@QueryParam("startDate") Long startDateEpochMillis,
                                  @QueryParam("endDate") Long endDateEpochMillis) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            log.info("User {} retrieving period tags", userId);
            List<PeriodTagDto> periodTags;
            if (startDateEpochMillis != null && endDateEpochMillis != null) {
                Instant startDate = Instant.ofEpochMilli(startDateEpochMillis);
                Instant endDate = Instant.ofEpochMilli(endDateEpochMillis);
                periodTags = service.getPeriodTagsForTimeRange(userId, startDate, endDate);
            } else {
                periodTags = service.getPeriodTags(userId);
            }

            return Response.ok(ApiResponse.success(periodTags)).build();
        } catch (Exception e) {
            log.error("Failed to retrieve period tags", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve period tags: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/active")
    public Response getActiveTag() {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            log.info("User {} retrieving active period tag", userId);
            Optional<PeriodTagDto> activeTag = service.getActiveTag(userId);
            return Response.ok(ApiResponse.success(activeTag.orElse(null))).build();
        } catch (Exception e) {
            log.error("Failed to retrieve active period tag", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve active period tag: " + e.getMessage()))
                    .build();
        }
    }


    @GET
    @Path("/check-overlaps")
    public Response checkOverlaps(@QueryParam("startTime") String startTimeStr,
                                  @QueryParam("endTime") String endTimeStr,
                                  @QueryParam("excludeId") Long excludeId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            log.info("User {} checking for overlapping period tags", userId);

            if (startTimeStr == null || endTimeStr == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error("startTime and endTime are required"))
                        .build();
            }

            Instant startTime = Instant.parse(startTimeStr);
            Instant endTime = Instant.parse(endTimeStr);

            List<PeriodTagDto> overlapping = service.checkOverlaps(userId, startTime, endTime, excludeId);
            return Response.ok(ApiResponse.success(overlapping)).build();
        } catch (Exception e) {
            log.error("Failed to check overlaps", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to check overlaps: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("")
    public Response createPeriodTag(@Valid CreatePeriodTagDto dto) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            log.info("User {} creating period tag '{}'", userId, dto.getTagName());

            PeriodTagDto response = service.createPeriodTag(userId, dto);

            return Response.status(Response.Status.CREATED)
                    .entity(ApiResponse.success(response))
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request to create period tag: {}", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to create period tag", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to create period tag: " + e.getMessage()))
                    .build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response updatePeriodTag(@PathParam("id") Long id, @Valid UpdatePeriodTagDto dto) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            log.info("User {} updating period tag {}", userId, id);

            PeriodTagDto updated = service.updatePeriodTag(userId, id, dto);
            return Response.ok(ApiResponse.success(updated)).build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request to update period tag {}: {}", id, e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to update period tag {}", id, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to update period tag: " + e.getMessage()))
                    .build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response deletePeriodTag(@PathParam("id") Long id) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            log.info("User {} deleting period tag {}", userId, id);

            service.deletePeriodTag(userId, id);
            return Response.ok(ApiResponse.success("Period tag deleted successfully")).build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request to delete period tag {}: {}", id, e.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Period tag not found"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to delete period tag {}", id, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to delete period tag: " + e.getMessage()))
                    .build();
        }
    }
}
