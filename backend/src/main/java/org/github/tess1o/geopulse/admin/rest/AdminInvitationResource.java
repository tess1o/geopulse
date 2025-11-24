package org.github.tess1o.geopulse.admin.rest;

import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.admin.dto.*;
import org.github.tess1o.geopulse.admin.model.InvitationStatus;
import org.github.tess1o.geopulse.admin.model.UserInvitationEntity;
import org.github.tess1o.geopulse.admin.service.UserInvitationService;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.UserIpAddress;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Path("/api/admin/invitations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
@Slf4j
public class AdminInvitationResource {

    @Context
    HttpServerRequest request;

    @Inject
    UserInvitationService invitationService;

    @Inject
    CurrentUserService currentUserService;

    @ConfigProperty(name = "geopulse.invitation.base-url", defaultValue = "")
    Optional<String> baseUrl;


    /**
     * Get all invitations with optional status filter
     */
    @GET
    public Response getInvitations(
            @QueryParam("status") InvitationStatus status,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size
    ) {
        try {
            List<InvitationResponse> invitations = invitationService.getInvitations(status, page, size);
            long total = invitationService.countInvitations(status);

            PagedResponse<InvitationResponse> response = PagedResponse.<InvitationResponse>builder()
                    .content(invitations)
                    .totalElements(total)
                    .totalPages((int) Math.ceil((double) total / size))
                    .page(page)
                    .size(size)
                    .build();

            return Response.ok(response).build();
        } catch (Exception e) {
            log.error("Error fetching invitations", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to fetch invitations"))
                    .build();
        }
    }

    /**
     * Get the configured base URL for invitation links
     */
    @GET
    @Path("/base-url")
    public Response getBaseUrl() {
        String url = this.baseUrl.orElse("");
        return Response.ok(Map.of("baseUrl", url)).build();
    }

    /**
     * Create a new invitation
     */
    @POST
    public Response createInvitation(
            @Valid CreateInvitationRequest createRequest,
            @HeaderParam("X-Forwarded-For") String forwardedFor,
            @HeaderParam("X-Real-IP") String realIp
    ) {
        try {
            UUID adminUserId = currentUserService.getCurrentUserId();
            String ipAddress = UserIpAddress.resolve(request, forwardedFor, realIp);

            UserInvitationEntity invitation = invitationService.createInvitation(
                    adminUserId,
                    createRequest.getExpiresAt(),
                    ipAddress
            );

            CreateInvitationResponse response = CreateInvitationResponse.builder()
                    .id(invitation.getId())
                    .token(invitation.getToken())
                    .baseUrl(baseUrl.orElse(""))
                    .expiresAt(invitation.getExpiresAt())
                    .build();

            return Response.status(Response.Status.CREATED).entity(response).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Error creating invitation", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to create invitation"))
                    .build();
        }
    }

    /**
     * Revoke an invitation
     */
    @DELETE
    @Path("/{id}")
    public Response revokeInvitation(
            @PathParam("id") UUID invitationId,
            @HeaderParam("X-Forwarded-For") String forwardedFor,
            @HeaderParam("X-Real-IP") String realIp
    ) {
        try {
            UUID adminUserId = currentUserService.getCurrentUserId();
            String ipAddress = UserIpAddress.resolve(request, forwardedFor, realIp);

            invitationService.revokeInvitation(invitationId, adminUserId, ipAddress);

            return Response.ok(Map.of("message", "Invitation revoked successfully")).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Error revoking invitation", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to revoke invitation"))
                    .build();
        }
    }
}
