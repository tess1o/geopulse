package org.github.tess1o.geopulse.trips.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.trips.model.dto.TripSummaryDto;
import org.github.tess1o.geopulse.trips.service.TripSummaryService;

import java.util.UUID;

@Path("/api/trips/{tripId}/summary")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Slf4j
public class TripSummaryResource {

    private final TripSummaryService tripSummaryService;
    private final CurrentUserService currentUserService;

    public TripSummaryResource(TripSummaryService tripSummaryService, CurrentUserService currentUserService) {
        this.tripSummaryService = tripSummaryService;
        this.currentUserService = currentUserService;
    }

    @GET
    public Response getTripSummary(@PathParam("tripId") Long tripId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            TripSummaryDto summary = tripSummaryService.getSummary(userId, tripId);
            return Response.ok(ApiResponse.success(summary)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Trip not found"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to get trip summary for trip {}", tripId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get trip summary"))
                    .build();
        }
    }
}

