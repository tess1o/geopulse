package org.github.tess1o.geopulse.friends.invitation.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
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

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.FRIEND_INVITATION_ACCESS_DENIED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.FRIEND_INVITATION_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.FRIEND_INVITATION_RECIPIENT_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.FRIEND_INVITATION_WRONG_STATUS;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_FRIEND_INVITATION;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/api/friends/invitations")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: Friends", description = "Send and manage friend invitations.")
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
    public RestResponse<FriendInvitationDTO> sendInvitation(@NotNull @Valid SendFriendInvitationDTO request) {
        try {
            UUID receiverId = userService.findByEmail(request.getReceiverEmail())
                    .orElseThrow(() -> new UserNotFoundException("Invitation recipient not found"))
                    .getId();
            FriendInvitationDTO invitation = friendInvitationService.sendInvitation(
                    currentUserService.getCurrentUserId(), receiverId);
            return RestResponse.status(Response.Status.CREATED, invitation);
        } catch (UserNotFoundException exception) {
            throw problem(FRIEND_INVITATION_RECIPIENT_NOT_FOUND, exception.getMessage());
        } catch (FriendsException exception) {
            throw problem(INVALID_FRIEND_INVITATION, exception.getMessage());
        }
    }

    @GET
    @Path("/received")
    public List<FriendInvitationDTO> getReceivedInvitations() {
        return friendInvitationService.getPendingInvitations(currentUserService.getCurrentUserId());
    }

    @GET
    @Path("/sent")
    public List<FriendInvitationDTO> getSentInvitations() {
        return friendInvitationService.getSentInvitations(currentUserService.getCurrentUserId());
    }

    @PUT
    @Path("/{invitationId}/accept")
    @Transactional
    public FriendInvitationDTO acceptInvitation(@PathParam("invitationId") Long invitationId) {
        return handleInvitation(invitationId, friendInvitationService::acceptInvitation);
    }

    @PUT
    @Path("/{invitationId}/reject")
    @Transactional
    public FriendInvitationDTO rejectInvitation(@PathParam("invitationId") Long invitationId) {
        return handleInvitation(invitationId, friendInvitationService::rejectInvitation);
    }

    @PUT
    @Path("/{invitationId}/cancel")
    @Transactional
    public FriendInvitationDTO cancelInvitation(@PathParam("invitationId") Long invitationId) {
        return handleInvitation(invitationId, friendInvitationService::cancelInvitation);
    }

    private FriendInvitationDTO handleInvitation(Long invitationId, InvitationAction action) {
        try {
            return action.apply(invitationId, currentUserService.getCurrentUserId());
        } catch (InvitationNotFoundException exception) {
            throw problem(FRIEND_INVITATION_NOT_FOUND, exception.getMessage(),
                    Map.of("invitationId", invitationId));
        } catch (NotAuthorizedUserException exception) {
            throw problem(FRIEND_INVITATION_ACCESS_DENIED, exception.getMessage(),
                    Map.of("invitationId", invitationId));
        } catch (InvitationWrongStatusException exception) {
            throw problem(FRIEND_INVITATION_WRONG_STATUS, exception.getMessage(),
                    Map.of("invitationId", invitationId));
        }
    }

    @FunctionalInterface
    private interface InvitationAction {
        FriendInvitationDTO apply(Long invitationId, UUID userId);
    }
}
