package org.github.tess1o.geopulse.mapmatching.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.mapmatching.dto.MapMatchingResolutionRequest;
import org.github.tess1o.geopulse.mapmatching.dto.MapMatchingStatusRequest;
import org.github.tess1o.geopulse.mapmatching.service.MapMatchingService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.util.UUID;

@Path("/api/map-matching")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MapMatchingResource {

    private final CurrentUserService currentUserService;
    private final MapMatchingService mapMatchingService;

    public MapMatchingResource(CurrentUserService currentUserService,
                               MapMatchingService mapMatchingService) {
        this.currentUserService = currentUserService;
        this.mapMatchingService = mapMatchingService;
    }

    @POST
    @Path("/resolve")
    @RolesAllowed({"USER", "ADMIN"})
    public Response resolve(@Valid MapMatchingResolutionRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            return Response.ok(ApiResponse.success(mapMatchingService.resolve(userId, request.getTripIds()))).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ApiResponse.error(e.getMessage())).build();
        }
    }

    @POST
    @Path("/status")
    @RolesAllowed({"USER", "ADMIN"})
    public Response status(@Valid MapMatchingStatusRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            return Response.ok(ApiResponse.success(mapMatchingService.status(userId, request.getTargetIds()))).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ApiResponse.error(e.getMessage())).build();
        }
    }
}
