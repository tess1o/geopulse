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
import org.jboss.resteasy.reactive.RestResponse;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

@Path("/registration-invitations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
@Slf4j
@Tag(name = "User: Authentication", description = "Validate invitations and register invited users.")
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
    public ValidateInvitationResponse validateToken(@PathParam("token") String token) {
        try {
            UserInvitationEntity invitation = invitationService.validateToken(token);

            ValidateInvitationResponse response = ValidateInvitationResponse.builder()
                    .valid(invitation.isValid())
                    .status(invitation.getStatus())
                    .message(getStatusMessage(invitation))
                    .build();

            return response;
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVITATION_NOT_FOUND, "Invalid invitation token", e);
        }
    }

    /**
     * Register a new user via invitation (public endpoint, bypasses registration checks)
     */
    @POST
    @Path("/{token}/registrations")
    public RestResponse<UserResponse> registerViaInvitation(
            @PathParam("token") String token,
            @Valid InvitationRegisterRequest request) {
        try {
            // Validate the invitation token first
            UserInvitationEntity invitation = invitationService.validateToken(token);

            if (!invitation.isValid()) {
                throw new GeoPulseException(INVALID_INVITATION, getStatusMessage(invitation).fallback());
            }

            // Register the user (this bypasses registration enabled checks)
            UserEntity user = userService.registerUserViaInvitation(
                    token,
                    request.getEmail(),
                    request.getPassword(),
                    request.getFullName(),
                    request.getTimezone()
            );

            // Mark invitation as used
            invitationService.markAsUsed(token, user.getId());

            UserResponse response = userMapper.toResponse(user);
            return RestResponse.status(Response.Status.CREATED, response);

        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_INVITATION, INVALID_INVITATION.title(), e);
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
