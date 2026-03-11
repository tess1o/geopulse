package org.github.tess1o.geopulse.trips.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.trips.model.dto.TripVisitSuggestionDto;
import org.github.tess1o.geopulse.trips.service.TripVisitAutoMatchService;

import java.util.List;
import java.util.UUID;

@Path("/api/trips/{tripId}/visit-suggestions")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Slf4j
public class TripVisitMatchingResource {

    private final TripVisitAutoMatchService tripVisitAutoMatchService;
    private final CurrentUserService currentUserService;

    public TripVisitMatchingResource(TripVisitAutoMatchService tripVisitAutoMatchService,
                                     CurrentUserService currentUserService) {
        this.tripVisitAutoMatchService = tripVisitAutoMatchService;
        this.currentUserService = currentUserService;
    }

    @GET
    public Response getVisitSuggestions(@PathParam("tripId") Long tripId,
                                        @QueryParam("applyAuto") @DefaultValue("false") boolean applyAuto) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            List<TripVisitSuggestionDto> suggestions = tripVisitAutoMatchService.evaluate(userId, tripId, applyAuto);
            return Response.ok(ApiResponse.success(suggestions)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Trip not found"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to evaluate trip visit suggestions for trip {}", tripId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to evaluate trip visit suggestions"))
                    .build();
        }
    }
}

