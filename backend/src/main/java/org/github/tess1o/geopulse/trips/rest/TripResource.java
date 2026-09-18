package org.github.tess1o.geopulse.trips.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.trips.model.dto.CreateTripDto;
import org.github.tess1o.geopulse.trips.model.dto.TripCollaboratorDto;
import org.github.tess1o.geopulse.trips.model.dto.TripDto;
import org.github.tess1o.geopulse.trips.model.dto.UpdateTripCollaboratorDto;
import org.github.tess1o.geopulse.trips.model.dto.UpdateTripDto;
import org.github.tess1o.geopulse.trips.service.TripService;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TRIP_REQUEST;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.PERIOD_TAG_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TRIP_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/trips")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: Trips", description = "Manage trips, collaborators, and period-tag links.")
public class TripResource {

    private final TripService tripService;
    private final CurrentUserService currentUserService;

    @Inject
    public TripResource(TripService tripService, CurrentUserService currentUserService) {
        this.tripService = tripService;
        this.currentUserService = currentUserService;
    }

    @GET
    public List<TripDto> getTrips(@QueryParam("status") String status) {
        try {
            return tripService.getTrips(currentUserService.getCurrentUserId(), status);
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
    }

    @GET
    @Path("/{id}")
    public TripDto getTrip(@PathParam("id") Long id) {
        try {
            return tripService.getTrip(currentUserService.getCurrentUserId(), id);
        } catch (NotFoundException e) {
            throw notFound(id);
        }
    }

    @POST
    public RestResponse<TripDto> createTrip(@Valid CreateTripDto dto) {
        try {
            return RestResponse.status(Response.Status.CREATED,
                    tripService.createTrip(currentUserService.getCurrentUserId(), dto));
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
    }

    @POST
    @Path("/from-period-tag/{periodTagId}")
    public RestResponse<TripDto> createTripFromPeriodTag(@PathParam("periodTagId") Long periodTagId) {
        try {
            return RestResponse.status(Response.Status.CREATED,
                    tripService.createTripFromPeriodTag(currentUserService.getCurrentUserId(), periodTagId));
        } catch (NotFoundException e) {
            throw problem(PERIOD_TAG_NOT_FOUND, "Period tag not found", Map.of("periodTagId", periodTagId));
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
    }

    @PUT
    @Path("/{id}")
    public TripDto updateTrip(@PathParam("id") Long id, @Valid UpdateTripDto dto) {
        try {
            return tripService.updateTrip(currentUserService.getCurrentUserId(), id, dto);
        } catch (NotFoundException e) {
            throw notFound(id);
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
    }

    @DELETE
    @Path("/{id}")
    public RestResponse<Void> deleteTrip(@PathParam("id") Long id,
                                         @QueryParam("mode") @DefaultValue("unlink_only") String mode) {
        if (!"unlink_only".equalsIgnoreCase(mode) && !"delete_both".equalsIgnoreCase(mode)) {
            throw problem(INVALID_TRIP_REQUEST, "Invalid delete mode",
                    Map.of("mode", mode, "allowedValues", "unlink_only,delete_both"));
        }
        try {
            tripService.deleteTrip(currentUserService.getCurrentUserId(), id, "delete_both".equalsIgnoreCase(mode));
            return RestResponse.noContent();
        } catch (NotFoundException e) {
            throw notFound(id);
        }
    }

    @DELETE
    @Path("/{id}/period-tags")
    public TripDto unlinkTripFromPeriodTag(@PathParam("id") Long id) {
        try {
            return tripService.unlinkTripFromPeriodTag(currentUserService.getCurrentUserId(), id);
        } catch (NotFoundException e) {
            throw notFound(id);
        }
    }

    @GET
    @Path("/{id}/collaborators")
    public List<TripCollaboratorDto> getTripCollaborators(@PathParam("id") Long id) {
        try {
            return tripService.getTripCollaborators(currentUserService.getCurrentUserId(), id);
        } catch (NotFoundException e) {
            throw notFound(id);
        }
    }

    @PUT
    @Path("/{id}/collaborators/{friendId}")
    public TripCollaboratorDto upsertTripCollaborator(
            @PathParam("id") Long id,
            @PathParam("friendId") String friendId,
            @Valid UpdateTripCollaboratorDto dto) {
        try {
            return tripService.upsertTripCollaborator(
                    currentUserService.getCurrentUserId(), id, UUID.fromString(friendId), dto);
        } catch (NotFoundException e) {
            throw notFound(id);
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
    }

    @DELETE
    @Path("/{id}/collaborators/{friendId}")
    public RestResponse<Void> removeTripCollaborator(
            @PathParam("id") Long id, @PathParam("friendId") String friendId) {
        try {
            tripService.removeTripCollaborator(
                    currentUserService.getCurrentUserId(), id, UUID.fromString(friendId));
            return RestResponse.noContent();
        } catch (NotFoundException e) {
            throw notFound(id);
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
    }

    private static io.quarkiverse.httpproblem.HttpProblem notFound(Long tripId) {
        return problem(TRIP_NOT_FOUND, "Trip not found", Map.of("tripId", tripId));
    }

    private static io.quarkiverse.httpproblem.HttpProblem invalid(IllegalArgumentException exception) {
        String detail = exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Invalid trip request"
                : exception.getMessage();
        return problem(INVALID_TRIP_REQUEST, detail);
    }
}
