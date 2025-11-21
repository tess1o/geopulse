package org.github.tess1o.geopulse.friends.invitation.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.friends.invitation.model.FriendInvitationDTO;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.friends.invitation.service.FriendInvitationService;
import org.github.tess1o.geopulse.friends.exceptions.FriendsException;
import org.github.tess1o.geopulse.friends.invitation.model.exceptions.InvitationNotFoundException;
import org.github.tess1o.geopulse.friends.invitation.model.exceptions.InvitationWrongStatusException;
import org.github.tess1o.geopulse.friends.invitation.model.SendFriendInvitationDTO;
import org.github.tess1o.geopulse.user.exceptions.NotAuthorizedUserException;
import org.github.tess1o.geopulse.user.exceptions.UserNotFoundException;
import org.github.tess1o.geopulse.user.service.UserService;

import java.util.List;
import java.util.UUID;

@Path("/api/friends/invitations")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@RolesAllowed({"USER", "ADMIN"})
public class FriendInvitationResource {
    private final FriendInvitationService friendInvitationService;
    private final CurrentUserService currentUserService;
    private final UserService userService;

    @Inject
    public FriendInvitationResource(FriendInvitationService friendInvitationService,
                                    CurrentUserService currentUserService, UserService userService) {
        this.friendInvitationService = friendInvitationService;
        this.currentUserService = currentUserService;
        this.userService = userService;
    }

    /**
     * Send a friend invitation to another user.
     *
     * @param dto The invitation data containing the receiver ID
     * @return The created invitation
     */
    @POST
    @Transactional
    public Response sendInvitation(@Valid SendFriendInvitationDTO dto) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            UUID receiverId = userService.findByEmail(dto.getReceiverEmail()).orElseThrow(() -> new UserNotFoundException("No user found: " + dto.getReceiverEmail())).getId();
            FriendInvitationDTO invitation = friendInvitationService.sendInvitation(userId, receiverId);
            return Response.status(Response.Status.CREATED).entity(ApiResponse.success(invitation)).build();
        } catch (UserNotFoundException userNotFoundException) {
            log.error("Failed to send invitation", userNotFoundException);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error(userNotFoundException.getMessage()))
                    .build();
        } catch (FriendsException friendsException) {
            log.error("Failed to send invitation", friendsException);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(friendsException.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to send invitation", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        }
    }

    /**
     * Get all pending invitations received by the current user.
     *
     * @return A list of pending invitations
     */
    @GET
    @Path("/received")
    public Response getReceivedInvitations() {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            List<FriendInvitationDTO> invitations = friendInvitationService.getPendingInvitations(userId);
            return Response.ok(ApiResponse.success(invitations)).build();
        } catch (Exception e) {
            log.error("Failed to get received invitations for user", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve received invitations"))
                    .build();
        }
    }

    /**
     * Get all pending invitations sent by the current user.
     *
     * @return A list of pending invitations
     */
    @GET
    @Path("/sent")
    public Response getSentInvitations() {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            List<FriendInvitationDTO> invitations = friendInvitationService.getSentInvitations(userId);
            return Response.ok(ApiResponse.success(invitations)).build();
        } catch (Exception e) {
            log.error("Failed to get sent invitations for user", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve sent invitations"))
                    .build();
        }
    }

    /**
     * Accept a friend's invitation.
     *
     * @param invitationId The ID of the invitation to accept
     * @return The updated invitation
     */
    @PUT
    @Path("/{invitationId}/accept")
    @Transactional
    public Response acceptInvitation(@PathParam("invitationId") Long invitationId) {
        UUID userId = currentUserService.getCurrentUserId();
        return handleInvitation(invitationId, userId, friendInvitationService::acceptInvitation);
    }

    /**
     * Reject a friend's invitation.
     *
     * @param invitationId The ID of the invitation to reject
     * @return The updated invitation
     */
    @PUT
    @Path("/{invitationId}/reject")
    @Transactional
    public Response rejectInvitation(@PathParam("invitationId") Long invitationId) {
        UUID userId = currentUserService.getCurrentUserId();
        return handleInvitation(invitationId, userId, friendInvitationService::rejectInvitation);
    }

    /**
     * Cancel a friend's invitation.
     *
     * @param invitationId The ID of the invitation to cancel
     * @return The updated invitation
     */
    @PUT
    @Path("/{invitationId}/cancel")
    @Transactional
    public Response cancelInvitation(@PathParam("invitationId") Long invitationId) {
        UUID userId = currentUserService.getCurrentUserId();
        return handleInvitation(invitationId, userId, friendInvitationService::cancelInvitation);
    }


    @FunctionalInterface
    interface InvitationAction {
        FriendInvitationDTO apply(Long invitationId, UUID userId);
    }

    private Response handleInvitation(Long invitationId, UUID userId, InvitationAction action) {
        try {
            FriendInvitationDTO invitation = action.apply(invitationId, userId);
            return Response.ok(ApiResponse.success(invitation)).build();
        } catch (InvitationNotFoundException notFoundException) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error(notFoundException.getMessage()))
                    .build();
        } catch (NotAuthorizedUserException notAuthorizedUserException) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error(notAuthorizedUserException.getMessage()))
                    .build();
        } catch (InvitationWrongStatusException wrongStatusException) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(wrongStatusException.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        }
    }

}
