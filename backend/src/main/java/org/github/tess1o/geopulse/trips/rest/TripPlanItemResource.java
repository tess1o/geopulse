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
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.trips.model.dto.CreateTripPlanItemDto;
import org.github.tess1o.geopulse.trips.model.dto.ReorderTripPlanItemsDto;
import org.github.tess1o.geopulse.trips.model.dto.TripPlanItemDto;
import org.github.tess1o.geopulse.trips.model.dto.TripPlanRouteDto;
import org.github.tess1o.geopulse.trips.model.dto.TripVisitOverrideRequestDto;
import org.github.tess1o.geopulse.trips.model.dto.UpdateTripPlanItemDto;
import org.github.tess1o.geopulse.trips.service.TripPlanItemService;
import org.github.tess1o.geopulse.trips.service.TripPlanRouteService;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TRIP_PLAN_ITEM;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TRIP_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TRIP_PLAN_ITEM_NOT_FOUND;

@Path("/trips/{tripId}/plan-items")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = ApiTags.TRIP_PLANNING)
public class TripPlanItemResource {

    private final TripPlanItemService service;
    private final TripPlanRouteService routeService;
    private final CurrentUserService currentUserService;

    @Inject
    public TripPlanItemResource(TripPlanItemService service,
                                TripPlanRouteService routeService,
                                CurrentUserService currentUserService) {
        this.service = service;
        this.routeService = routeService;
        this.currentUserService = currentUserService;
    }

    @GET
    @Operation(summary = "List plan items",
            description = "Returns the places planned for a trip, in plan order, with their visit status.")
    public List<TripPlanItemDto> getPlanItems(
            @Parameter(description = "Trip ID.")
            @PathParam("tripId") Long tripId) {
        try {
            return service.getTripPlanItems(currentUserService.getCurrentUserId(), tripId);
        } catch (NotFoundException e) {
            throw notFound(tripId, null, e);
        }
    }

    @GET
    @Path("/route")
    @Operation(summary = "Get the planned route",
            description = "Returns a line through the trip's plan items in plan order. Each leg follows roads or "
                    + "paths when a Valhalla routing server is configured, and is a straight line otherwise, or "
                    + "when it is too long to route.")
    public TripPlanRouteDto getPlanRoute(
            @Parameter(description = "Trip ID.")
            @PathParam("tripId") Long tripId) {
        try {
            return routeService.getPlanRoute(currentUserService.getCurrentUserId(), tripId);
        } catch (NotFoundException e) {
            throw notFound(tripId, null, e);
        }
    }

    @POST
    @Operation(summary = "Add a plan item",
            description = "Adds a place to visit to a trip. Requires edit access to the trip.")
    public RestResponse<TripPlanItemDto> createPlanItem(
            @Parameter(description = "Trip ID.")
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
    @Operation(summary = "Update a plan item",
            description = "Changes a plan item, such as its title, location, planned day, or notes.")
    public TripPlanItemDto updatePlanItem(
            @Parameter(description = "Trip ID.")
            @PathParam("tripId") Long tripId,
            @Parameter(description = "Plan item ID.")
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

    @PUT
    @Path("/order")
    @Operation(summary = "Reorder plan items",
            description = "Sets the order of the trip's plan items. Returns the items in their new order.")
    public List<TripPlanItemDto> reorderPlanItems(
            @Parameter(description = "Trip ID.")
            @PathParam("tripId") Long tripId, @Valid ReorderTripPlanItemsDto dto) {
        try {
            return service.reorderTripPlanItems(currentUserService.getCurrentUserId(), tripId, dto);
        } catch (NotFoundException e) {
            throw notFound(tripId, null, e);
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
    }

    @DELETE
    @Path("/{itemId}")
    @Operation(summary = "Delete a plan item",
            description = "Removes a place from the trip plan.")
    public RestResponse<Void> deletePlanItem(
            @Parameter(description = "Trip ID.")
            @PathParam("tripId") Long tripId,
            @Parameter(description = "Plan item ID.")
            @PathParam("itemId") Long itemId) {
        try {
            service.deleteTripPlanItem(currentUserService.getCurrentUserId(), tripId, itemId);
            return RestResponse.noContent();
        } catch (NotFoundException e) {
            throw notFound(tripId, itemId, e);
        }
    }

    @PUT
    @Path("/{itemId}/visit-override")
    @Operation(summary = "Mark a plan item as visited or not",
            description = "Overrides automatic visit matching for a plan item: `CONFIRM_VISITED` (optionally with "
                    + "`visitedAt`), `REJECT_VISIT`, or `RESET_TO_AUTO`.")
    public TripPlanItemDto applyVisitOverride(
            @Parameter(description = "Trip ID.")
            @PathParam("tripId") Long tripId,
            @Parameter(description = "Plan item ID.")
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
