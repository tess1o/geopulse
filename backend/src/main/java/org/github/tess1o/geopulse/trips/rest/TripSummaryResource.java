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

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TRIP_NOT_FOUND;

@Path("/trips/{tripId}/summary")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: Trips", description = "Read trip summaries.")
public class TripSummaryResource {

    private final TripSummaryService tripSummaryService;
    private final CurrentUserService currentUserService;

    public TripSummaryResource(TripSummaryService tripSummaryService, CurrentUserService currentUserService) {
        this.tripSummaryService = tripSummaryService;
        this.currentUserService = currentUserService;
    }

    @GET
    public TripSummaryDto getTripSummary(@PathParam("tripId") Long tripId) {
        try {
            return tripSummaryService.getSummary(currentUserService.getCurrentUserId(), tripId);
        } catch (NotFoundException e) {
            throw new GeoPulseException(TRIP_NOT_FOUND, "Trip not found", Map.of("tripId", tripId), e);
        }
    }
}
