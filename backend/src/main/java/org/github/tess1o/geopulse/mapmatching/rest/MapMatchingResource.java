package org.github.tess1o.geopulse.mapmatching.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.mapmatching.dto.MapMatchingResolutionRequest;
import org.github.tess1o.geopulse.mapmatching.dto.MapMatchingResolutionResponse;
import org.github.tess1o.geopulse.mapmatching.dto.MapMatchingStatusRequest;
import org.github.tess1o.geopulse.mapmatching.dto.MapMatchingTripResolutionDTO;
import org.github.tess1o.geopulse.mapmatching.service.MapMatchingService;

import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.github.tess1o.geopulse.shared.openapi.ApiExtensions;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_MAP_MATCHING_REQUEST;

@Path("/map-matching-jobs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = ApiTags.TIMELINE)
public class MapMatchingResource {

    private final CurrentUserService currentUserService;
    private final MapMatchingService mapMatchingService;

    public MapMatchingResource(CurrentUserService currentUserService,
                               MapMatchingService mapMatchingService) {
        this.currentUserService = currentUserService;
        this.mapMatchingService = mapMatchingService;
    }

    @POST
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(summary = "Snap trips to roads",
            description = "Returns road-snapped paths for up to 100 timeline trips, when map matching is enabled for "
                    + "the user. Trips that are not matched yet are queued; their entries say when to check again "
                    + "(`pollAfterMs`). Check them with `POST /api/v1/map-matching-jobs/searches`.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public MapMatchingResolutionResponse resolve(@NotNull @Valid MapMatchingResolutionRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            return mapMatchingService.resolve(userId, request.getTripIds());
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_MAP_MATCHING_REQUEST, INVALID_MAP_MATCHING_REQUEST.title(), e);
        }
    }

    @POST
    @Path("/searches")
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(summary = "Check map matching results",
            description = "Returns the current state and, when finished, the snapped path of up to 100 queued map "
                    + "matching targets.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public List<MapMatchingTripResolutionDTO> status(@NotNull @Valid MapMatchingStatusRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            return mapMatchingService.status(userId, request.getTargetIds());
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_MAP_MATCHING_REQUEST, INVALID_MAP_MATCHING_REQUEST.title(), e);
        }
    }
}
