package org.github.tess1o.geopulse.trips.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.gps.model.GpsPointPathDTO;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.trips.service.TripWorkspaceDataService;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@Path("/api/trips/{tripId}")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Slf4j
public class TripWorkspaceDataResource {

    private final TripWorkspaceDataService tripWorkspaceDataService;
    private final CurrentUserService currentUserService;

    public TripWorkspaceDataResource(TripWorkspaceDataService tripWorkspaceDataService,
                                     CurrentUserService currentUserService) {
        this.tripWorkspaceDataService = tripWorkspaceDataService;
        this.currentUserService = currentUserService;
    }

    @GET
    @Path("/timeline")
    public Response getTripTimeline(@PathParam("tripId") Long tripId,
                                    @QueryParam("startTime") String startTime,
                                    @QueryParam("endTime") String endTime) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            Instant parsedStart = parseInstant(startTime);
            Instant parsedEnd = parseInstant(endTime);
            MovementTimelineDTO timeline = tripWorkspaceDataService.getTripTimeline(userId, tripId, parsedStart, parsedEnd);
            return Response.ok(ApiResponse.success(timeline)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Trip not found"))
                    .build();
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to get trip timeline for trip {}", tripId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get trip timeline"))
                    .build();
        }
    }

    @GET
    @Path("/path")
    public Response getTripPath(@PathParam("tripId") Long tripId,
                                @QueryParam("startTime") String startTime,
                                @QueryParam("endTime") String endTime) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            Instant parsedStart = parseInstant(startTime);
            Instant parsedEnd = parseInstant(endTime);
            GpsPointPathDTO path = tripWorkspaceDataService.getTripPath(userId, tripId, parsedStart, parsedEnd);
            return Response.ok(ApiResponse.success(path)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Trip not found"))
                    .build();
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to get trip path for trip {}", tripId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get trip path"))
                    .build();
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }
}

