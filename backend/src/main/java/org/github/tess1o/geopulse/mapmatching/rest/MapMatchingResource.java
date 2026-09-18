package org.github.tess1o.geopulse.mapmatching.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.mapmatching.dto.MapMatchingResolutionRequest;
import org.github.tess1o.geopulse.mapmatching.dto.MapMatchingResolutionResponse;
import org.github.tess1o.geopulse.mapmatching.dto.MapMatchingStatusRequest;
import org.github.tess1o.geopulse.mapmatching.dto.MapMatchingTripResolutionDTO;
import org.github.tess1o.geopulse.mapmatching.service.MapMatchingService;

import java.util.List;
import java.util.UUID;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_MAP_MATCHING_REQUEST;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/map-matching-jobs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "User: Trips and Planning", description = "Resolve map matching jobs for trips.")
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
    public MapMatchingResolutionResponse resolve(@NotNull @Valid MapMatchingResolutionRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            return mapMatchingService.resolve(userId, request.getTripIds());
        } catch (IllegalArgumentException e) {
            throw problem(INVALID_MAP_MATCHING_REQUEST, e.getMessage());
        }
    }

    @POST
    @Path("/searches")
    @RolesAllowed({"USER", "ADMIN"})
    public List<MapMatchingTripResolutionDTO> status(@NotNull @Valid MapMatchingStatusRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            return mapMatchingService.status(userId, request.getTargetIds());
        } catch (IllegalArgumentException e) {
            throw problem(INVALID_MAP_MATCHING_REQUEST, e.getMessage());
        }
    }
}
