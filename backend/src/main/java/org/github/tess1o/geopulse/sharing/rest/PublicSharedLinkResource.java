package org.github.tess1o.geopulse.sharing.rest;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.sharing.model.*;
import org.github.tess1o.geopulse.sharing.service.SharedLinkService;

import java.util.UUID;

@Path("/api/shared")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Slf4j
public class PublicSharedLinkResource {

    @Inject
    SharedLinkService sharedLinkService;

    @GET
    @Path("/{linkId}/info")
    public Response getSharedLocationInfo(@PathParam("linkId") UUID linkId) {
        try {
            SharedLocationInfo result = sharedLinkService.getSharedLocationInfo(linkId);
            return Response.ok(result).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Link not found or expired"))
                    .build();
        } catch (Exception e) {
            log.error("Error getting shared location info for linkId: {}", linkId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve link information"))
                    .build();
        }
    }

    @POST
    @Path("/{linkId}/verify")
    public Response verifyPassword(@PathParam("linkId") UUID linkId, @Valid VerifyPasswordRequest request) {
        try {
            AccessTokenResponse result = sharedLinkService.verifyPassword(linkId, request.getPassword());
            return Response.ok(result).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Link not found or expired"))
                    .build();
        } catch (ForbiddenException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("Invalid password"))
                    .build();
        } catch (Exception e) {
            log.error("Error verifying password for linkId: {}", linkId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Verification failed"))
                    .build();
        }
    }

    @GET
    @Path("/{linkId}/location")
    public Response getSharedLocation(@PathParam("linkId") UUID linkId, @HeaderParam("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ApiResponse.error("Authorization token required"))
                        .build();
            }

            String token = authHeader.substring("Bearer ".length());
            LocationHistoryResponse result = sharedLinkService.getSharedLocation(linkId, token);
            return Response.ok(result).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Link not found or expired"))
                    .build();
        } catch (ForbiddenException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("Access denied"))
                    .build();
        } catch (Exception e) {
            log.error("Error getting shared location for linkId: {}", linkId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve location"))
                    .build();
        }
    }
}