package org.github.tess1o.geopulse.trips.rest;

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
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

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
            throw problem(TRIP_NOT_FOUND, "Trip not found", Map.of("tripId", tripId));
        } catch (IllegalArgumentException e) {
            String detail = e.getMessage() == null || e.getMessage().isBlank()
                    ? "Invalid trip request"
                    : e.getMessage();
            throw problem(INVALID_TRIP_REQUEST, detail);
        }
    }
}
