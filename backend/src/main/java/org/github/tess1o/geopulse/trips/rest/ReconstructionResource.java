package org.github.tess1o.geopulse.trips.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionCommitResponseDto;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionPreviewDto;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionRequestDto;
import org.github.tess1o.geopulse.trips.service.TripReconstructionService;

import java.util.UUID;

@Path("/api/reconstruction")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Slf4j
public class ReconstructionResource {

    private final TripReconstructionService tripReconstructionService;
    private final CurrentUserService currentUserService;

    public ReconstructionResource(TripReconstructionService tripReconstructionService,
                                  CurrentUserService currentUserService) {
        this.tripReconstructionService = tripReconstructionService;
        this.currentUserService = currentUserService;
    }

    @POST
    @Path("/preview")
    public Response preview(@Valid TripReconstructionRequestDto request) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            TripReconstructionPreviewDto preview = tripReconstructionService.preview(userId, request);
            return Response.ok(ApiResponse.success(preview)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Trip not found"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to preview reconstruction", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to preview reconstruction"))
                    .build();
        }
    }

    @POST
    @Path("/commit")
    public Response commit(@Valid TripReconstructionRequestDto request) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            TripReconstructionCommitResponseDto result = tripReconstructionService.commit(userId, request);
            return Response.ok(ApiResponse.success(result)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Trip not found"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to commit reconstruction", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to commit reconstruction"))
                    .build();
        }
    }
}
