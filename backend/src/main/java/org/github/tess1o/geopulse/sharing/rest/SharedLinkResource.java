package org.github.tess1o.geopulse.sharing.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.sharing.exceptions.TooManyLinksException;
import org.github.tess1o.geopulse.sharing.model.*;
import org.github.tess1o.geopulse.sharing.service.SharedLinkService;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.jboss.resteasy.reactive.RestResponse;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

@Path("/share-links")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = ApiTags.SHARE_LINKS)
public class SharedLinkResource {

    @Inject
    SharedLinkService sharedLinkService;

    @Inject
    CurrentUserService currentUserService;

    @GET
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(summary = "List share links",
            description = "Returns the share links of the signed-in user with their type, settings, expiration, and "
                    + "view count, plus the per-user link limit.")
    public SharedLinksDto getSharedLinks() {
        try {
            return sharedLinkService.getSharedLinks(currentUserService.getCurrentUserId());
        } catch (SecurityException e) {
            throw new GeoPulseException(AUTHENTICATION_REQUIRED, "Unauthorized", e);
        }
    }

    @POST
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(summary = "Create a share link",
            description = "Creates a public link. `shareType` is `LIVE_LOCATION` (your current location, optionally "
                    + "with the last `historyHours` of movement) or `TIMELINE` (the timeline between `startDate` "
                    + "and `endDate`, optionally with notes and Immich photos). Links can expire and can require a "
                    + "password. The number of links per user is limited.")
    public RestResponse<CreateShareLinkResponse> createShareLink(@Valid CreateShareLinkRequest request) {
        try {
            UserEntity currentUser = currentUserService.getCurrentUser();
            return RestResponse.status(Response.Status.CREATED,
                    sharedLinkService.createShareLink(request, currentUser));
        } catch (TooManyLinksException e) {
            throw new GeoPulseException(SHARED_LINK_LIMIT_EXCEEDED, SHARED_LINK_LIMIT_EXCEEDED.title(), e);
        } catch (SecurityException e) {
            throw new GeoPulseException(AUTHENTICATION_REQUIRED, "Unauthorized", e);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_SHARE_LINK, INVALID_SHARE_LINK.title(), e);
        }
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(summary = "Update a share link",
            description = "Changes the name, expiration, password, or display options of a share link.")
    public SharedLinkDto updateShareLink(
            @Parameter(description = "Share link ID.")
            @PathParam("id") UUID id, @Valid UpdateShareLinkDto updateDto) {
        try {
            return sharedLinkService.updateShareLink(id, updateDto, currentUserService.getCurrentUserId());
        } catch (NotFoundException e) {
            throw new GeoPulseException(SHARED_LINK_NOT_FOUND, "Link not found", e);
        } catch (SecurityException e) {
            throw new GeoPulseException(AUTHENTICATION_REQUIRED, "Unauthorized", e);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_SHARE_LINK, INVALID_SHARE_LINK.title(), e);
        }
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(summary = "Delete a share link",
            description = "Deletes a share link. The link stops working immediately.")
    public void deleteShareLink(
            @Parameter(description = "Share link ID.")
            @PathParam("id") UUID id) {
        try {
            sharedLinkService.deleteShareLink(id, currentUserService.getCurrentUserId());
        } catch (NotFoundException e) {
            throw new GeoPulseException(SHARED_LINK_NOT_FOUND, "Link not found", e);
        } catch (SecurityException e) {
            throw new GeoPulseException(AUTHENTICATION_REQUIRED, "Unauthorized", e);
        }
    }
}
