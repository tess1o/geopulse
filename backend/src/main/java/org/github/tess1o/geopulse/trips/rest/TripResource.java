package org.github.tess1o.geopulse.trips.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.trips.model.dto.CreateTripDto;
import org.github.tess1o.geopulse.trips.model.dto.TripDto;
import org.github.tess1o.geopulse.trips.model.dto.UpdateTripDto;
import org.github.tess1o.geopulse.trips.service.TripService;

import java.util.List;
import java.util.UUID;

@Path("/api/trips")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Slf4j
public class TripResource {

    private final TripService tripService;
    private final CurrentUserService currentUserService;

    @Inject
    public TripResource(TripService tripService, CurrentUserService currentUserService) {
        this.tripService = tripService;
        this.currentUserService = currentUserService;
    }

    @GET
    public Response getTrips(@QueryParam("status") String status) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            List<TripDto> trips = tripService.getTrips(userId, status);
            return Response.ok(ApiResponse.success(trips)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to get trips", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get trips"))
                    .build();
        }
    }

    @GET
    @Path("/{id}")
    public Response getTrip(@PathParam("id") Long id) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            TripDto trip = tripService.getTrip(userId, id);
            return Response.ok(ApiResponse.success(trip)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Trip not found"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to get trip {}", id, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get trip"))
                    .build();
        }
    }

    @POST
    public Response createTrip(@Valid CreateTripDto dto) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            TripDto created = tripService.createTrip(userId, dto);
            return Response.status(Response.Status.CREATED)
                    .entity(ApiResponse.success(created))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to create trip", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to create trip"))
                    .build();
        }
    }

    @POST
    @Path("/from-period-tag/{periodTagId}")
    public Response createTripFromPeriodTag(@PathParam("periodTagId") Long periodTagId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            TripDto created = tripService.createTripFromPeriodTag(userId, periodTagId);
            return Response.status(Response.Status.CREATED)
                    .entity(ApiResponse.success(created))
                    .build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Period tag not found"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to create trip from period tag {}", periodTagId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to create trip from period tag"))
                    .build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response updateTrip(@PathParam("id") Long id, @Valid UpdateTripDto dto) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            TripDto updated = tripService.updateTrip(userId, id, dto);
            return Response.ok(ApiResponse.success(updated)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Trip not found"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to update trip {}", id, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to update trip"))
                    .build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response deleteTrip(@PathParam("id") Long id,
                               @QueryParam("mode") @DefaultValue("unlink_only") String mode) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            if (!"unlink_only".equalsIgnoreCase(mode) && !"delete_both".equalsIgnoreCase(mode)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error("Invalid delete mode. Supported values: unlink_only, delete_both"))
                        .build();
            }
            tripService.deleteTrip(userId, id, "delete_both".equalsIgnoreCase(mode));
            return Response.ok(ApiResponse.success("Trip deleted successfully")).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Trip not found"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to delete trip {}", id, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to delete trip"))
                    .build();
        }
    }

    @POST
    @Path("/{id}/unlink")
    public Response unlinkTripFromPeriodTag(@PathParam("id") Long id) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            TripDto updated = tripService.unlinkTripFromPeriodTag(userId, id);
            return Response.ok(ApiResponse.success(updated)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Trip not found"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to unlink trip {}", id, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to unlink trip"))
                    .build();
        }
    }
}
