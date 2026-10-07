package org.github.tess1o.geopulse.friends.invitation.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.friends.exceptions.FriendsException;
import org.github.tess1o.geopulse.friends.invitation.model.FriendInvitationDTO;
import org.github.tess1o.geopulse.friends.invitation.model.SendFriendInvitationDTO;
import org.github.tess1o.geopulse.friends.invitation.model.exceptions.InvitationNotFoundException;
import org.github.tess1o.geopulse.friends.invitation.model.exceptions.InvitationWrongStatusException;
import org.github.tess1o.geopulse.friends.invitation.service.FriendInvitationService;
import org.github.tess1o.geopulse.user.exceptions.NotAuthorizedUserException;
import org.github.tess1o.geopulse.user.exceptions.UserNotFoundException;
import org.github.tess1o.geopulse.user.service.UserService;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.FRIEND_INVITATION_ACCESS_DENIED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.FRIEND_INVITATION_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.FRIEND_INVITATION_RECIPIENT_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.FRIEND_INVITATION_WRONG_STATUS;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_FRIEND_INVITATION;

@Path("/friend-invitations")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = ApiTags.FRIENDS)
public class FriendInvitationResource {

    private final FriendInvitationService friendInvitationService;
    private final CurrentUserService currentUserService;
    private final UserService userService;

    @Inject
    public FriendInvitationResource(FriendInvitationService friendInvitationService,
                                    CurrentUserService currentUserService,
                                    UserService userService) {
        this.friendInvitationService = friendInvitationService;
        this.currentUserService = currentUserService;
        this.userService = userService;
    }

    @POST
    @Transactional
    @APIResponse(responseCode = "201", description = "Friend invitation created")
    @Operation(summary = "Send a friend invitation",
            description = "Invites another user of this GeoPulse server, identified by email, to become friends.")
    public RestResponse<FriendInvitationDTO> sendInvitation(@NotNull @Valid SendFriendInvitationDTO request) {
        try {
            UUID receiverId = userService.findByEmail(request.getReceiverEmail())
                    .orElseThrow(() -> new UserNotFoundException("Invitation recipient not found"))
                    .getId();
            FriendInvitationDTO invitation = friendInvitationService.sendInvitation(
                    currentUserService.getCurrentUserId(), receiverId);
            return RestResponse.status(Response.Status.CREATED, invitation);
        } catch (UserNotFoundException exception) {
            throw new GeoPulseException(FRIEND_INVITATION_RECIPIENT_NOT_FOUND, FRIEND_INVITATION_RECIPIENT_NOT_FOUND.title(), exception);
        } catch (FriendsException exception) {
            throw new GeoPulseException(INVALID_FRIEND_INVITATION, INVALID_FRIEND_INVITATION.title(), exception);
        }
    }

    @GET
    @Path("/received")
    @Operation(summary = "List received invitations",
            description = "Returns pending friend invitations sent to the signed-in user.")
    public List<FriendInvitationDTO> getReceivedInvitations() {
        return friendInvitationService.getPendingInvitations(currentUserService.getCurrentUserId());
    }

    @GET
    @Path("/sent")
    @Operation(summary = "List sent invitations",
            description = "Returns friend invitations the signed-in user has sent.")
    public List<FriendInvitationDTO> getSentInvitations() {
        return friendInvitationService.getSentInvitations(currentUserService.getCurrentUserId());
    }

    @POST
    @Path("/{invitationId}/accept")
    @Transactional
    @Operation(summary = "Accept a friend invitation",
            description = "Accepts an invitation sent to you, and you and the sender become friends. Choose what you "
                    + "share with the permission endpoints.")
    public FriendInvitationDTO acceptInvitation(
            @Parameter(description = "Friend invitation ID.")
            @PathParam("invitationId") Long invitationId) {
        return handleInvitation(invitationId, friendInvitationService::acceptInvitation);
    }

    @POST
    @Path("/{invitationId}/reject")
    @Transactional
    @Operation(summary = "Reject a friend invitation",
            description = "Declines an invitation sent to you.")
    public FriendInvitationDTO rejectInvitation(
            @Parameter(description = "Friend invitation ID.")
            @PathParam("invitationId") Long invitationId) {
        return handleInvitation(invitationId, friendInvitationService::rejectInvitation);
    }

    @DELETE
    @Path("/{invitationId}")
    @Transactional
    @Operation(summary = "Cancel a friend invitation",
            description = "Withdraws a pending invitation you sent.")
    public FriendInvitationDTO cancelInvitation(
            @Parameter(description = "Friend invitation ID.")
            @PathParam("invitationId") Long invitationId) {
        return handleInvitation(invitationId, friendInvitationService::cancelInvitation);
    }

    private FriendInvitationDTO handleInvitation(Long invitationId, InvitationAction action) {
        try {
            return action.apply(invitationId, currentUserService.getCurrentUserId());
        } catch (InvitationNotFoundException exception) {
            throw new GeoPulseException(FRIEND_INVITATION_NOT_FOUND, FRIEND_INVITATION_NOT_FOUND.title(),
                    Map.of("invitationId", invitationId), exception);
        } catch (NotAuthorizedUserException exception) {
            throw new GeoPulseException(FRIEND_INVITATION_ACCESS_DENIED, FRIEND_INVITATION_ACCESS_DENIED.title(),
                    Map.of("invitationId", invitationId), exception);
        } catch (InvitationWrongStatusException exception) {
            throw new GeoPulseException(FRIEND_INVITATION_WRONG_STATUS, FRIEND_INVITATION_WRONG_STATUS.title(),
                    Map.of("invitationId", invitationId), exception);
        }
    }

    @FunctionalInterface
    private interface InvitationAction {
        FriendInvitationDTO apply(Long invitationId, UUID userId);
    }
}
