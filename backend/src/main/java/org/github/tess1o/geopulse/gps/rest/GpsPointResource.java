package org.github.tess1o.geopulse.gps.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.gps.model.GpsPointPathDTO;
import org.github.tess1o.geopulse.gps.model.GpsPointPathPointDTO;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.timeline.simplification.GpsPathSimplifier;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * REST resource for GPS point data.
 */
@Path("/api/gps")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
public class GpsPointResource {

    private final GpsPointService gpsPointService;
    private final CurrentUserService currentUserService;

    @Inject
    public GpsPointResource(GpsPointService gpsPointService, CurrentUserService currentUserService) {
        this.gpsPointService = gpsPointService;
        this.currentUserService = currentUserService;
    }

    /**
     * Get a GPS point path for a user within a specified time period.
     * This endpoint requires authentication.
     *
     * @param startTime The start of the time period (ISO-8601 format)
     * @param endTime   The end of the time period (ISO-8601 format)
     * @return The GPS point path
     */
    @GET
    @Path("/path")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("USER")
    public Response getGpsPointPath(
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Received request to get GPS point path for user {} between {} and {}", userId, startTime, endTime);

        try {
            Instant start = startTime != null ? Instant.parse(startTime) : Instant.EPOCH;
            Instant end = endTime != null ? Instant.parse(endTime) : Instant.now();
            GpsPointPathDTO path = gpsPointService.getGpsPointPath(userId, start, end);
            List<GpsPointPathPointDTO> simplifyPath = GpsPathSimplifier.simplifyPath(path.getPoints(), 10);
            path.setPoints(simplifyPath);
            return Response.ok(ApiResponse.success(path)).build();
        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid time format. Use ISO-8601 format (e.g., 2023-01-01T00:00:00Z)"))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve GPS point path: " + e.getMessage()))
                    .build();
        }
    }

}