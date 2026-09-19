package org.github.tess1o.geopulse.trips.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.streaming.model.dto.TripClassificationDetailsDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TripMovementTypeUpdateRequest;
import org.github.tess1o.geopulse.streaming.model.dto.TripMovementTypeUpdateResponseDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TripStaySplitRequest;
import org.github.tess1o.geopulse.streaming.model.dto.TripStaySplitResponse;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.service.TripClassificationDetailsService;
import org.github.tess1o.geopulse.streaming.service.TripMovementTypeOverrideService;
import org.github.tess1o.geopulse.streaming.service.TripStaySplitOverrideService;

import java.util.Arrays;
import java.util.Map;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TIMELINE_REQUEST;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TRIP_NOT_FOUND;

/**
 * Timeline overrides scoped to a single trip.
 *
 * <p>The class template deliberately declares only the literal sub-paths below and no
 * root method, so {@code /trips/{tripId}/timeline} itself stays owned by
 * {@code TripWorkspaceDataResource} and {@code /trips/{id}} by {@code TripResource}.</p>
 */
@Path("/trips/{tripId}/timeline")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@RequestScoped
@Tag(name = "User: Trips", description = "Manage per-trip timeline overrides.")
public class TripTimelineResource {

    @Inject
    CurrentUserService currentUserService;
    @Inject
    TripClassificationDetailsService tripClassificationDetailsService;
    @Inject
    TripMovementTypeOverrideService tripMovementTypeOverrideService;
    @Inject
    TripStaySplitOverrideService tripStaySplitOverrideService;

    @GET
    @Path("/classification")
    public TripClassificationDetailsDTO getTripClassificationDetails(@PathParam("tripId") Long tripId) {
        return tripClassificationDetailsService
                .getTripClassificationDetails(tripId, currentUserService.getCurrentUserId())
                .orElseThrow(() -> new GeoPulseException(TRIP_NOT_FOUND, "Trip not found or access denied",
                        Map.of("tripId", tripId)));
    }

    @PUT
    @Path("/movement-type")
    public TripMovementTypeUpdateResponseDTO updateTripMovementType(
            @PathParam("tripId") Long tripId, TripMovementTypeUpdateRequest request) {
        if (request == null || request.getMovementType() == null || request.getMovementType().isBlank()) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "movementType is required");
        }
        TripType movementType;
        try {
            movementType = TripType.valueOf(request.getMovementType().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Invalid movementType",
                    Map.of("movementType", request.getMovementType(), "allowedValues",
                            String.join(",", Arrays.stream(TripType.values()).map(Enum::name).toList())), e);
        }
        return tripMovementTypeOverrideService
                .setManualMovementType(currentUserService.getCurrentUserId(), tripId, movementType)
                .orElseThrow(() -> new GeoPulseException(TRIP_NOT_FOUND, "Trip not found or access denied",
                        Map.of("tripId", tripId)));
    }

    @DELETE
    @Path("/movement-type")
    public TripMovementTypeUpdateResponseDTO resetTripMovementType(@PathParam("tripId") Long tripId) {
        return tripMovementTypeOverrideService
                .resetToAutomaticMovementType(currentUserService.getCurrentUserId(), tripId)
                .orElseThrow(() -> new GeoPulseException(TRIP_NOT_FOUND, "Trip not found or access denied",
                        Map.of("tripId", tripId)));
    }

    @POST
    @Path("/stay-split/preview")
    public TripStaySplitResponse previewTripStaySplit(
            @PathParam("tripId") Long tripId, TripStaySplitRequest request) {
        try {
            return tripStaySplitOverrideService
                    .previewSplit(currentUserService.getCurrentUserId(), tripId, request)
                    .orElseThrow(() -> new GeoPulseException(TRIP_NOT_FOUND, "Trip not found or access denied",
                            Map.of("tripId", tripId)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Invalid trip split request", e);
        }
    }

    @PUT
    @Path("/stay-split")
    public TripStaySplitResponse splitTripWithStay(
            @PathParam("tripId") Long tripId, TripStaySplitRequest request) {
        try {
            return tripStaySplitOverrideService
                    .splitTrip(currentUserService.getCurrentUserId(), tripId, request)
                    .orElseThrow(() -> new GeoPulseException(TRIP_NOT_FOUND, "Trip not found or access denied",
                            Map.of("tripId", tripId)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Invalid trip split request", e);
        }
    }
}
