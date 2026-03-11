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
import org.github.tess1o.geopulse.trips.model.dto.CreateTripPlanItemDto;
import org.github.tess1o.geopulse.trips.model.dto.TripPlanItemDto;
import org.github.tess1o.geopulse.trips.model.dto.TripVisitOverrideRequestDto;
import org.github.tess1o.geopulse.trips.model.dto.UpdateTripPlanItemDto;
import org.github.tess1o.geopulse.trips.service.TripPlanItemService;

import java.util.List;
import java.util.UUID;

@Path("/api/trips/{tripId}/plan-items")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Slf4j
public class TripPlanItemResource {

    private final TripPlanItemService tripPlanItemService;
    private final CurrentUserService currentUserService;

    @Inject
    public TripPlanItemResource(TripPlanItemService tripPlanItemService,
                                CurrentUserService currentUserService) {
        this.tripPlanItemService = tripPlanItemService;
        this.currentUserService = currentUserService;
    }

    @GET
    public Response getPlanItems(@PathParam("tripId") Long tripId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            List<TripPlanItemDto> items = tripPlanItemService.getTripPlanItems(userId, tripId);
            return Response.ok(ApiResponse.success(items)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Trip not found"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to get plan items for trip {}", tripId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get trip plan items"))
                    .build();
        }
    }

    @POST
    public Response createPlanItem(@PathParam("tripId") Long tripId, @Valid CreateTripPlanItemDto dto) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            TripPlanItemDto created = tripPlanItemService.createTripPlanItem(userId, tripId, dto);
            return Response.status(Response.Status.CREATED)
                    .entity(ApiResponse.success(created))
                    .build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Trip not found"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to create plan item for trip {}", tripId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to create trip plan item"))
                    .build();
        }
    }

    @PUT
    @Path("/{itemId}")
    public Response updatePlanItem(@PathParam("tripId") Long tripId,
                                   @PathParam("itemId") Long itemId,
                                   @Valid UpdateTripPlanItemDto dto) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            TripPlanItemDto updated = tripPlanItemService.updateTripPlanItem(userId, tripId, itemId, dto);
            return Response.ok(ApiResponse.success(updated)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Trip or plan item not found"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to update plan item {} for trip {}", itemId, tripId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to update trip plan item"))
                    .build();
        }
    }

    @DELETE
    @Path("/{itemId}")
    public Response deletePlanItem(@PathParam("tripId") Long tripId, @PathParam("itemId") Long itemId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            tripPlanItemService.deleteTripPlanItem(userId, tripId, itemId);
            return Response.ok(ApiResponse.success("Trip plan item deleted successfully")).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Trip or plan item not found"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to delete plan item {} for trip {}", itemId, tripId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to delete trip plan item"))
                    .build();
        }
    }

    @POST
    @Path("/{itemId}/visit-override")
    public Response applyVisitOverride(@PathParam("tripId") Long tripId,
                                       @PathParam("itemId") Long itemId,
                                       @Valid TripVisitOverrideRequestDto request) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            TripPlanItemDto updated = tripPlanItemService.applyVisitOverride(userId, tripId, itemId, request);
            return Response.ok(ApiResponse.success(updated)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Trip or plan item not found"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to apply visit override for plan item {} in trip {}", itemId, tripId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to apply visit override"))
                    .build();
        }
    }
}
