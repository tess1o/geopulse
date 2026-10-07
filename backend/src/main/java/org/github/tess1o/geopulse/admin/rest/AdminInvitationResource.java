package org.github.tess1o.geopulse.admin.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

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
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.jboss.resteasy.reactive.RestResponse;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.github.tess1o.geopulse.shared.openapi.ApiExtensions;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_INVITATION;

@Path("/admin/invitations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = ApiTags.ADMIN_INVITATIONS)
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
    @Operation(summary = "List invitations",
            description = "Returns registration invitations one page at a time, optionally filtered by status.")
    public PageResponse<InvitationResponse> getInvitations(
            @Parameter(description = "Only invitations with this status: `PENDING`, `USED`, `EXPIRED`, or `REVOKED`.",
                    example = "PENDING")
            @QueryParam("status") InvitationStatus status,
            @Parameter(description = "Page number, starting at 0.")
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @Parameter(description = "Page size, from 1 to 200. Defaults to 50.")
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
    @Operation(summary = "Get the invitation base URL",
            description = "Returns the base URL configured for invitation links (`GEOPULSE_INVITATION_BASE_URL`), or "
                    + "an empty string when links should use the address the administrator is browsing from.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public InvitationBaseUrlResponse getBaseUrl() {
        return new InvitationBaseUrlResponse(baseUrl.orElse(""));
    }

    /**
     * Create a new invitation
     */
    @POST
    @RolesAllowed(SecurityRoles.ADMIN)
    @Operation(summary = "Create an invitation",
            description = "Creates a single-use registration invitation, optionally with an expiration time. The "
                    + "response contains the invitation token; the link is `<base URL>/register/invite/<token>`. "
                    + "Invitations work even when open registration is disabled.")
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
            throw new GeoPulseException(INVALID_INVITATION, INVALID_INVITATION.title(), e);
        }
    }

    /**
     * Revoke an invitation
     */
    @DELETE
    @Path("/{id}")
    @RolesAllowed(SecurityRoles.ADMIN)
    @Operation(summary = "Revoke an invitation",
            description = "Revokes an unused invitation so its link stops working.")
    public void revokeInvitation(
            @Parameter(description = "Invitation ID.")
            @PathParam("id") UUID invitationId) {
        try {
            UUID adminUserId = currentUserService.getCurrentUserId();
            String ipAddress = UserIpAddress.resolve(request);

            invitationService.revokeInvitation(invitationId, adminUserId, ipAddress);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_INVITATION, INVALID_INVITATION.title(), e);
        }
    }
}
