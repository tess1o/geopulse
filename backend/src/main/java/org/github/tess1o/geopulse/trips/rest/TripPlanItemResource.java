package org.github.tess1o.geopulse.trips.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.trips.model.dto.CreateTripPlanItemDto;
import org.github.tess1o.geopulse.trips.model.dto.TripPlanItemDto;
import org.github.tess1o.geopulse.trips.model.dto.TripVisitOverrideRequestDto;
import org.github.tess1o.geopulse.trips.model.dto.UpdateTripPlanItemDto;
import org.github.tess1o.geopulse.trips.service.TripPlanItemService;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;
import java.util.Map;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TRIP_PLAN_ITEM;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TRIP_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TRIP_PLAN_ITEM_NOT_FOUND;

@Path("/trips/{tripId}/plan-items")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: Trips and Planning", description = "Manage trip plan items and visit overrides.")
public class TripPlanItemResource {

    private final TripPlanItemService service;
    private final CurrentUserService currentUserService;

    @Inject
    public TripPlanItemResource(TripPlanItemService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GET
    public List<TripPlanItemDto> getPlanItems(@PathParam("tripId") Long tripId) {
        try {
            return service.getTripPlanItems(currentUserService.getCurrentUserId(), tripId);
        } catch (NotFoundException e) {
            throw notFound(tripId, null, e);
        }
    }

    @POST
    public RestResponse<TripPlanItemDto> createPlanItem(
            @PathParam("tripId") Long tripId, @Valid CreateTripPlanItemDto dto) {
        try {
            return RestResponse.status(Response.Status.CREATED,
                    service.createTripPlanItem(currentUserService.getCurrentUserId(), tripId, dto));
        } catch (NotFoundException e) {
            throw notFound(tripId, null, e);
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
    }

    @PUT
    @Path("/{itemId}")
    public TripPlanItemDto updatePlanItem(
            @PathParam("tripId") Long tripId,
            @PathParam("itemId") Long itemId,
            @Valid UpdateTripPlanItemDto dto) {
        try {
            return service.updateTripPlanItem(currentUserService.getCurrentUserId(), tripId, itemId, dto);
        } catch (NotFoundException e) {
            throw notFound(tripId, itemId, e);
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
    }

    @DELETE
    @Path("/{itemId}")
    public RestResponse<Void> deletePlanItem(
            @PathParam("tripId") Long tripId, @PathParam("itemId") Long itemId) {
        try {
            service.deleteTripPlanItem(currentUserService.getCurrentUserId(), tripId, itemId);
            return RestResponse.noContent();
        } catch (NotFoundException e) {
            throw notFound(tripId, itemId, e);
        }
    }

    @PUT
    @Path("/{itemId}/visit-override")
    public TripPlanItemDto applyVisitOverride(
            @PathParam("tripId") Long tripId,
            @PathParam("itemId") Long itemId,
            @Valid TripVisitOverrideRequestDto request) {
        try {
            return service.applyVisitOverride(currentUserService.getCurrentUserId(), tripId, itemId, request);
        } catch (NotFoundException e) {
            throw notFound(tripId, itemId, e);
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
    }

    private static GeoPulseException notFound(Long tripId, Long itemId, NotFoundException cause) {
        return itemId == null
                ? new GeoPulseException(TRIP_NOT_FOUND, "Trip not found", Map.of("tripId", tripId), cause)
                : new GeoPulseException(TRIP_PLAN_ITEM_NOT_FOUND, "Trip or plan item not found",
                        Map.of("tripId", tripId, "itemId", itemId), cause);
    }

    private static GeoPulseException invalid(IllegalArgumentException exception) {
        return new GeoPulseException(INVALID_TRIP_PLAN_ITEM, "Invalid trip plan item", exception);
    }
}
