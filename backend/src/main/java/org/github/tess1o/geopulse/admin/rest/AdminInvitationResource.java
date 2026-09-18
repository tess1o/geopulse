package org.github.tess1o.geopulse.admin.rest;

import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.github.tess1o.geopulse.auth.security.SecurityRoles;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.PageResponse;
import org.github.tess1o.geopulse.shared.api.UserIpAddress;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestResponse;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_INVITATION;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/admin/invitations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = "Admin: Invitations", description = "Manage account invitations and registration links.")
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
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.DEMO_ADMIN_READ})
    public PageResponse<InvitationResponse> getInvitations(
            @QueryParam("status") InvitationStatus status,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("50") @Min(1) @Max(200) int size
    ) {
        List<InvitationResponse> invitations = invitationService.getInvitations(status, page, size);
        long total = invitationService.countInvitations(status);

        return new PageResponse<>(invitations, page, size, total, (int) Math.ceil((double) total / size));
    }

    /**
     * Get the configured base URL for invitation links
     */
    @GET
    @Path("/base-url")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.DEMO_ADMIN_READ})
    public InvitationBaseUrlResponse getBaseUrl() {
        return new InvitationBaseUrlResponse(baseUrl.orElse(""));
    }

    /**
     * Create a new invitation
     */
    @POST
    @RolesAllowed(SecurityRoles.ADMIN)
    public RestResponse<CreateInvitationResponse> createInvitation(@Valid CreateInvitationRequest createRequest) {
        try {
            UUID adminUserId = currentUserService.getCurrentUserId();
            String ipAddress = UserIpAddress.resolve(request);

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

            return RestResponse.status(Response.Status.CREATED, response);
        } catch (IllegalArgumentException e) {
            throw problem(INVALID_INVITATION, e.getMessage());
        }
    }

    /**
     * Revoke an invitation
     */
    @DELETE
    @Path("/{id}")
    @RolesAllowed(SecurityRoles.ADMIN)
    public void revokeInvitation(@PathParam("id") UUID invitationId) {
        try {
            UUID adminUserId = currentUserService.getCurrentUserId();
            String ipAddress = UserIpAddress.resolve(request);

            invitationService.revokeInvitation(invitationId, adminUserId, ipAddress);
        } catch (IllegalArgumentException e) {
            throw problem(INVALID_INVITATION, e.getMessage());
        }
    }
}
