package org.github.tess1o.geopulse.trips.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.trips.model.dto.TripSummaryDto;
import org.github.tess1o.geopulse.trips.service.TripSummaryService;

import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TRIP_NOT_FOUND;

@Path("/trips/{tripId}/summary")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = ApiTags.TRIPS)
public class TripSummaryResource {

    private final TripSummaryService tripSummaryService;
    private final CurrentUserService currentUserService;

    public TripSummaryResource(TripSummaryService tripSummaryService, CurrentUserService currentUserService) {
        this.tripSummaryService = tripSummaryService;
        this.currentUserService = currentUserService;
    }

    @GET
    @Operation(summary = "Get a trip summary",
            description = "Returns totals for a trip: plan items visited out of planned, number of stays and "
                    + "movements, distance, and time spent moving.")
    public TripSummaryDto getTripSummary(
            @Parameter(description = "Trip ID.")
            @PathParam("tripId") Long tripId) {
        try {
            return tripSummaryService.getSummary(currentUserService.getCurrentUserId(), tripId);
        } catch (NotFoundException e) {
            throw new GeoPulseException(TRIP_NOT_FOUND, "Trip not found", Map.of("tripId", tripId), e);
        }
    }
}
