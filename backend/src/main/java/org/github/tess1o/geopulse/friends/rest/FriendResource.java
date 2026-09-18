package org.github.tess1o.geopulse.friends.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.friends.exceptions.FriendsException;
import org.github.tess1o.geopulse.friends.model.FriendInfoDTO;
import org.github.tess1o.geopulse.friends.model.FriendLocationTrailDTO;
import org.github.tess1o.geopulse.friends.model.UpdateLiveLocationPermissionRequest;
import org.github.tess1o.geopulse.friends.model.UpdateTimelinePermissionRequest;
import org.github.tess1o.geopulse.friends.model.UserFriendPermissionDTO;
import org.github.tess1o.geopulse.friends.service.FriendService;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.model.GpsPointPathPointDTO;
import org.github.tess1o.geopulse.user.exceptions.NotAuthorizedUserException;
import org.github.tess1o.geopulse.user.model.UserSearchDTO;
import org.jboss.resteasy.reactive.RestResponse;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.FRIEND_LOCATION_ACCESS_DENIED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.FRIEND_LOCATION_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.FRIEND_RELATIONSHIP_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_FRIEND_ID;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_FRIEND_TRAIL_RANGE;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/friends")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: Friends", description = "Manage friends, location sharing, permissions, and friend discovery.")
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

    @GET
    public List<FriendInfoDTO> getFriends() {
        return friendService.getAllFriends(currentUserService.getCurrentUserId());
    }

    @DELETE
    @Path("/{friendId}")
    @Transactional
    @APIResponse(responseCode = "204", description = "Friend removed")
    public RestResponse<Void> removeFriend(@PathParam("friendId") @NotNull String friendId) {
        UUID parsedFriendId = parseFriendId(friendId);
        try {
            friendService.removeFriend(currentUserService.getCurrentUserId(), parsedFriendId);
            return RestResponse.noContent();
        } catch (FriendsException exception) {
            throw problem(FRIEND_RELATIONSHIP_NOT_FOUND, exception.getMessage(), Map.of("friendId", parsedFriendId.toString()));
        }
    }

    @GET
    @Path("/{friendId}/location")
    public GpsPointPathPointDTO getFriendLocation(@PathParam("friendId") @NotNull String friendId) {
        UUID parsedFriendId = parseFriendId(friendId);
        try {
            GpsPointEntity location = friendService.getFriendLocation(
                    currentUserService.getCurrentUserId(), parsedFriendId);
            if (location == null) {
                throw problem(FRIEND_LOCATION_NOT_FOUND, "Friend location not found",
                        Map.of("friendId", parsedFriendId.toString()));
            }
            return gpsPointMapper.toPathPoint(location);
        } catch (NotAuthorizedUserException exception) {
            throw problem(FRIEND_LOCATION_ACCESS_DENIED, exception.getMessage(),
                    Map.of("friendId", parsedFriendId.toString()));
        }
    }

    @GET
    @Path("/trails")
    public List<FriendLocationTrailDTO> getFriendsLocationTrails(
            @QueryParam("minutes") @DefaultValue("60") Integer minutes,
            @QueryParam("to") String endTime) {
        if (minutes == null || minutes <= 0 || minutes > 1440) {
            throw problem(INVALID_FRIEND_TRAIL_RANGE, "minutes must be between 1 and 1440",
                    Map.of("min", 1, "max", 1440));
        }

        Instant requestedEndTime;
        try {
            requestedEndTime = endTime != null && !endTime.isBlank() ? Instant.parse(endTime) : Instant.now();
        } catch (DateTimeParseException exception) {
            throw problem(INVALID_FRIEND_TRAIL_RANGE, "endTime must use ISO-8601 format");
        }

        return friendService.getFriendsLocationHistory(
                        currentUserService.getCurrentUserId(), minutes, requestedEndTime)
                .entrySet().stream()
                .map(entry -> new FriendLocationTrailDTO(
                        entry.getKey(),
                        entry.getValue().stream().map(gpsPointMapper::toPathPoint).toList()))
                .toList();
    }

    @GET
    @Path("/candidates")
    public List<UserSearchDTO> searchUsersToInvite(@QueryParam("q") @NotNull String query) {
        return friendService.searchUsersToInvite(currentUserService.getCurrentUserId(), query);
    }

    @GET
    @Path("/{friendId}/permissions")
    public UserFriendPermissionDTO getFriendPermissions(@PathParam("friendId") @NotNull String friendId) {
        UUID parsedFriendId = parseFriendId(friendId);
        try {
            return friendService.getFriendPermissions(currentUserService.getCurrentUserId(), parsedFriendId);
        } catch (FriendsException exception) {
            throw problem(FRIEND_RELATIONSHIP_NOT_FOUND, exception.getMessage(), Map.of("friendId", parsedFriendId.toString()));
        }
    }

    @PUT
    @Path("/{friendId}/permissions")
    @Transactional
    public UserFriendPermissionDTO updateFriendPermissions(
            @PathParam("friendId") @NotNull String friendId,
            @NotNull @Valid UpdateTimelinePermissionRequest request) {
        UUID parsedFriendId = parseFriendId(friendId);
        try {
            return friendService.updateFriendPermissions(
                    currentUserService.getCurrentUserId(), parsedFriendId, request.shareTimeline());
        } catch (FriendsException exception) {
            throw problem(FRIEND_RELATIONSHIP_NOT_FOUND, exception.getMessage(), Map.of("friendId", parsedFriendId.toString()));
        }
    }

    @GET
    @Path("/permissions")
    public List<UserFriendPermissionDTO> getAllFriendPermissions() {
        return friendService.getAllFriendPermissions(currentUserService.getCurrentUserId());
    }

    @PUT
    @Path("/{friendId}/permissions/live")
    @Transactional
    public UserFriendPermissionDTO updateLiveLocationPermission(
            @PathParam("friendId") @NotNull String friendId,
            @NotNull @Valid UpdateLiveLocationPermissionRequest request) {
        UUID parsedFriendId = parseFriendId(friendId);
        try {
            return friendService.updateLiveLocationPermission(
                    currentUserService.getCurrentUserId(), parsedFriendId, request.shareLiveLocation());
        } catch (FriendsException exception) {
            throw problem(FRIEND_RELATIONSHIP_NOT_FOUND, exception.getMessage(), Map.of("friendId", parsedFriendId.toString()));
        }
    }

    private UUID parseFriendId(String friendId) {
        try {
            return UUID.fromString(friendId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw problem(INVALID_FRIEND_ID, "Friend ID must be a UUID");
        }
    }
}
