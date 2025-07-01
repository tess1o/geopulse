package org.github.tess1o.geopulse.sharing.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.sharing.exceptions.TooManyLinksException;
import org.github.tess1o.geopulse.sharing.model.*;
import org.github.tess1o.geopulse.sharing.service.SharedLinkService;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.util.UUID;

@Path("/api/share-links")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Slf4j
public class SharedLinkResource {

    @Inject
    SharedLinkService sharedLinkService;

    @Inject
    CurrentUserService currentUserService;

    @GET
    @RolesAllowed({"USER", "ADMIN"})
    public Response getSharedLinks() {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            SharedLinksDto result = sharedLinkService.getSharedLinks(userId);
            return Response.ok(ApiResponse.success(result)).build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiResponse.error("Unauthorized"))
                    .build();
        } catch (Exception e) {
            log.error("Error getting shared links", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve shared links"))
                    .build();
        }
    }

    @POST
    @RolesAllowed({"USER", "ADMIN"})
    public Response createShareLink(@Valid CreateShareLinkRequest request) {
        try {
            UserEntity currentUser = currentUserService.getCurrentUser();
            CreateShareLinkResponse result = sharedLinkService.createShareLink(request, currentUser);
            return Response.status(Response.Status.CREATED).entity(result).build();
        } catch (TooManyLinksException e) {
            return Response.status(Response.Status.TOO_MANY_REQUESTS)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiResponse.error("Unauthorized"))
                    .build();
        } catch (Exception e) {
            log.error("Error creating share link", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to create share link"))
                    .build();
        }
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({"USER", "ADMIN"})
    public Response updateShareLink(@PathParam("id") UUID id, @Valid UpdateShareLinkDto updateDto) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            SharedLinkDto result = sharedLinkService.updateShareLink(id, updateDto, userId);
            return Response.ok(result).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Link not found"))
                    .build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiResponse.error("Unauthorized"))
                    .build();
        } catch (Exception e) {
            log.error("Error updating share link", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to update share link"))
                    .build();
        }
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({"USER", "ADMIN"})
    public Response deleteShareLink(@PathParam("id") UUID id) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            sharedLinkService.deleteShareLink(id, userId);
            return Response.noContent().build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Link not found"))
                    .build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiResponse.error("Unauthorized"))
                    .build();
        } catch (Exception e) {
            log.error("Error deleting share link", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to delete share link"))
                    .build();
        }
    }
}