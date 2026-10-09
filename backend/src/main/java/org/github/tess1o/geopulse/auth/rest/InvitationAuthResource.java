package org.github.tess1o.geopulse.auth.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.dto.InvitationRegisterRequest;
import org.github.tess1o.geopulse.admin.dto.ValidateInvitationResponse;
import org.github.tess1o.geopulse.admin.model.UserInvitationEntity;
import org.github.tess1o.geopulse.admin.service.UserInvitationService;
import org.github.tess1o.geopulse.user.mapper.UserMapper;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.model.UserResponse;
import org.github.tess1o.geopulse.user.service.UserService;
import org.github.tess1o.geopulse.shared.api.MessageDescriptor;

import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.jboss.resteasy.reactive.RestResponse;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

@Path("/registration-invitations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
@Slf4j
@Tag(name = ApiTags.REGISTRATION)
public class InvitationAuthResource {

    @Inject
    UserInvitationService invitationService;

    @Inject
    UserService userService;

    @Inject
    UserMapper userMapper;

    /**
     * Validate an invitation token (public endpoint)
     */
    @GET
    @Path("/{token}")
    @Operation(summary = "Check an invitation",
            description = "Returns whether an invitation link can still be used, and its status (pending, used, "
                    + "expired, or revoked).")
    public ValidateInvitationResponse validateToken(
            @Parameter(description = "Invitation token from the invitation link.")
            @PathParam("token") String token) {
        UserInvitationEntity invitation = findInvitation(token);
        return ValidateInvitationResponse.builder()
                .valid(invitation.isValid())
                .status(invitation.getStatus())
                .message(getStatusMessage(invitation))
                .build();
    }

    /**
     * Register a new user via invitation (public endpoint, bypasses registration checks)
     */
    @POST
    @Path("/{token}/registrations")
    @Operation(summary = "Register with an invitation",
            description = "Creates an account using an invitation link. Works even when open registration is "
                    + "disabled. The invitation is marked as used. The new user signs in afterwards.")
    public RestResponse<UserResponse> registerViaInvitation(
            @Parameter(description = "Invitation token from the invitation link.")
            @PathParam("token") String token,
            @Valid InvitationRegisterRequest request) {
        UserInvitationEntity invitation = findInvitation(token);
        if (!invitation.isValid()) {
            // The status lets the client say why: used, expired or revoked.
            throw new GeoPulseException(INVALID_INVITATION, getStatusMessage(invitation).fallback(),
                    Map.of("status", invitation.getStatus().name()));
        }

        // Register the user (this bypasses registration enabled checks). An email that is already
        // registered fails here with USER_REGISTRATION_CONFLICT.
        UserEntity user = userService.registerUserViaInvitation(
                token,
                request.getEmail(),
                request.getPassword(),
                request.getFullName(),
                request.getTimezone(),
                request.getLanguage()
        );

        try {
            invitationService.markAsUsed(token, user.getId());
        } catch (IllegalArgumentException e) {
            // Another registration consumed the invitation between the check above and now.
            throw new GeoPulseException(INVALID_INVITATION, INVALID_INVITATION.title(), e);
        }

        UserResponse response = userMapper.toResponse(user);
        return RestResponse.status(Response.Status.CREATED, response);
    }

    private UserInvitationEntity findInvitation(String token) {
        try {
            return invitationService.validateToken(token);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVITATION_NOT_FOUND, "Invalid invitation token", e);
        }
    }

    /**
     * Get human-readable status message
     */
    private MessageDescriptor getStatusMessage(UserInvitationEntity invitation) {
        return switch (invitation.getStatus()) {
            case PENDING -> new MessageDescriptor("invitations.status.pending", Map.of(),
                    "Invitation is valid and ready to use");
            case USED -> new MessageDescriptor("invitations.status.used", Map.of(),
                    "This invitation has already been used");
            case EXPIRED -> new MessageDescriptor("invitations.status.expired", Map.of(),
                    "This invitation has expired");
            case REVOKED -> new MessageDescriptor("invitations.status.revoked", Map.of(),
                    "This invitation has been revoked");
        };
    }
}
