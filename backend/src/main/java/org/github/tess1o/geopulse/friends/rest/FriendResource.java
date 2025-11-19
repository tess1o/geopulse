package org.github.tess1o.geopulse.friends.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.friends.exceptions.FriendsException;
import org.github.tess1o.geopulse.friends.model.FriendInfoDTO;
import org.github.tess1o.geopulse.friends.service.FriendService;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.model.GpsPointPathPointDTO;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.user.exceptions.NotAuthorizedUserException;
import org.github.tess1o.geopulse.user.model.UserSearchDTO;

import java.util.List;
import java.util.UUID;

@Path("/api/friends")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Slf4j
public class FriendResource {

    private final FriendService friendService;
    private final GpsPointMapper gpsPointMapper;
    private final CurrentUserService currentUserService;

    @Inject
    public FriendResource(FriendService friendService,
                          GpsPointMapper gpsPointMapper,
                          CurrentUserService currentUserService) {
        this.friendService = friendService;
        this.gpsPointMapper = gpsPointMapper;
        this.currentUserService = currentUserService;
    }

    /**
     * Get the list of friends for the current user.
     *
     * @return A list of friend IDs
     */
    @GET
    public Response getFriends() {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            List<FriendInfoDTO> allFriends = friendService.getAllFriends(userId);
            return Response.ok(ApiResponse.success(allFriends)).build();
        } catch (Exception e) {
            log.error("Failed to get friends for user", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve friends"))
                    .build();
        }
    }

    /**
     * Remove a friend.
     *
     * @param friendId The ID of the friend to remove
     * @return 204 No Content if successful
     */
    @DELETE
    @Path("/{friendId}")
    @Transactional
    public Response removeFriend(@PathParam("friendId") @NotNull String friendId) {
        try {
            if (friendId == null || friendId.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error("Friend ID cannot be empty"))
                        .build();
            }
            
            UUID userId = currentUserService.getCurrentUserId();
            UUID friendIdUUID = UUID.fromString(friendId);
            friendService.removeFriend(userId, friendIdUUID);
            return Response.ok(ApiResponse.success("Friend removed successfully")).build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid friend ID format: {}", friendId, e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid friend ID format"))
                    .build();
        } catch (FriendsException e) {
            log.warn("Failed to remove friend: {}", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to remove friend {} for user", friendId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to remove friend"))
                    .build();
        }
    }

    /**
     * Get a friend's current location.
     *
     * @param friendId The ID of the friend
     * @return The friend's current location
     */
    @GET
    @Path("/{friendId}/location")
    public Response getFriendLocation(
            @PathParam("friendId") @NotNull String friendId) {
        try {
            if (friendId == null || friendId.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error("Friend ID cannot be empty"))
                        .build();
            }
            
            UUID userId = currentUserService.getCurrentUserId();
            UUID friendIdUUID = UUID.fromString(friendId);
            GpsPointEntity location = friendService.getFriendLocation(userId, friendIdUUID);
            
            if (location == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ApiResponse.error("Friend location not found"))
                        .build();
            }
            
            GpsPointPathPointDTO locationDTO = gpsPointMapper.toPathPoint(location);
            return Response.ok(ApiResponse.success(locationDTO)).build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid friend ID format: {}", friendId, e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid friend ID format"))
                    .build();
        } catch (NotAuthorizedUserException e) {
            log.warn("Unauthorized access to friend location: {}", e.getMessage());
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to get friend location {} for user", friendId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve friend location"))
                    .build();
        }
    }

    /**
     * Search for users to invite as friends.
     *
     * @param query The search query string (email or full name).
     * @return A list of UserSearchDTOs.
     */
    @GET
    @Path("/search-users-to-invite")
    public Response searchUsersToInvite(@QueryParam("query") @NotNull String query) {
        try {
            UUID currentUserId = currentUserService.getCurrentUserId();
            List<UserSearchDTO> users = friendService.searchUsersToInvite(currentUserId, query);
            return Response.ok(ApiResponse.success(users)).build();
        } catch (Exception e) {
            log.error("Failed to search users to invite for user {}", currentUserService.getCurrentUserId(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to search users"))
                    .build();
        }
    }
}
