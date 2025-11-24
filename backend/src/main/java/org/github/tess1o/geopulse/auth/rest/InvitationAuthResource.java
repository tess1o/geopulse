package org.github.tess1o.geopulse.auth.rest;

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
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.util.Map;

@Path("/api/auth/invitation")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
@Slf4j
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
    @Path("/{token}/validate")
    public Response validateToken(@PathParam("token") String token) {
        try {
            UserInvitationEntity invitation = invitationService.validateToken(token);

            ValidateInvitationResponse response = ValidateInvitationResponse.builder()
                    .valid(invitation.isValid())
                    .status(invitation.getStatus())
                    .message(getStatusMessage(invitation))
                    .build();

            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            ValidateInvitationResponse response = ValidateInvitationResponse.builder()
                    .valid(false)
                    .status(null)
                    .message("Invalid invitation token")
                    .build();

            return Response.status(Response.Status.NOT_FOUND).entity(response).build();
        } catch (Exception e) {
            log.error("Error validating invitation token", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to validate invitation"))
                    .build();
        }
    }

    /**
     * Register a new user via invitation (public endpoint, bypasses registration checks)
     */
    @POST
    @Path("/register")
    public Response registerViaInvitation(@Valid InvitationRegisterRequest request) {
        try {
            // Validate the invitation token first
            UserInvitationEntity invitation = invitationService.validateToken(request.getToken());

            if (!invitation.isValid()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error(getStatusMessage(invitation)))
                        .build();
            }

            // Register the user (this bypasses registration enabled checks)
            UserEntity user = userService.registerUserViaInvitation(
                    request.getToken(),
                    request.getEmail(),
                    request.getPassword(),
                    request.getFullName(),
                    request.getTimezone()
            );

            // Mark invitation as used
            invitationService.markAsUsed(request.getToken(), user.getId());

            UserResponse response = userMapper.toResponse(user);
            return Response.status(Response.Status.CREATED)
                    .entity(ApiResponse.success(response))
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Registration via invitation failed: {}", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Error registering user via invitation", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Registration failed"))
                    .build();
        }
    }

    /**
     * Get human-readable status message
     */
    private String getStatusMessage(UserInvitationEntity invitation) {
        return switch (invitation.getStatus()) {
            case PENDING -> "Invitation is valid and ready to use";
            case USED -> "This invitation has already been used";
            case EXPIRED -> "This invitation has expired";
            case REVOKED -> "This invitation has been revoked";
        };
    }
}
