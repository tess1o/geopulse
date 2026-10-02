package org.github.tess1o.geopulse.trips.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.trips.model.dto.TripVisitSuggestionDto;
import org.github.tess1o.geopulse.trips.service.TripVisitAutoMatchService;

import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TRIP_REQUEST;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TRIP_NOT_FOUND;

@Path("/trips/{tripId}/visit-suggestions")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: Trips", description = "Read visit suggestions for trips.")
public class TripVisitMatchingResource {

    private final TripVisitAutoMatchService tripVisitAutoMatchService;
    private final CurrentUserService currentUserService;

    public TripVisitMatchingResource(TripVisitAutoMatchService tripVisitAutoMatchService,
                                     CurrentUserService currentUserService) {
        this.tripVisitAutoMatchService = tripVisitAutoMatchService;
        this.currentUserService = currentUserService;
    }

    @GET
    public List<TripVisitSuggestionDto> getVisitSuggestions(@PathParam("tripId") Long tripId) {
        try {
            return tripVisitAutoMatchService.getStoredSuggestions(currentUserService.getCurrentUserId(), tripId);
        } catch (NotFoundException e) {
            throw new GeoPulseException(TRIP_NOT_FOUND, "Trip not found", Map.of("tripId", tripId), e);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_TRIP_REQUEST, "Invalid trip request", e);
        }
    }
}
