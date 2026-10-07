package org.github.tess1o.geopulse.friends.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

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
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.FRIEND_LOCATION_ACCESS_DENIED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.FRIEND_LOCATION_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.FRIEND_RELATIONSHIP_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_FRIEND_ID;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_FRIEND_TRAIL_RANGE;

@Path("/friends")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = ApiTags.FRIENDS)
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
    @Operation(summary = "List friends",
            description = "Returns the friends of the signed-in user, with their last known location when they share "
                    + "their live location, and which permissions each side has granted.")
    public List<FriendInfoDTO> getFriends() {
        return friendService.getAllFriends(currentUserService.getCurrentUserId());
    }

    @DELETE
    @Path("/{friendId}")
    @Transactional
    @APIResponse(responseCode = "204", description = "Friend removed")
    @Operation(summary = "Remove a friend",
            description = "Ends the friendship for both users. Shared permissions are removed.")
    public RestResponse<Void> removeFriend(
            @Parameter(description = "User ID of the friend.")
            @PathParam("friendId") @NotNull String friendId) {
        UUID parsedFriendId = parseFriendId(friendId);
        try {
            friendService.removeFriend(currentUserService.getCurrentUserId(), parsedFriendId);
            return RestResponse.noContent();
        } catch (FriendsException exception) {
            throw new GeoPulseException(FRIEND_RELATIONSHIP_NOT_FOUND, FRIEND_RELATIONSHIP_NOT_FOUND.title(), Map.of("friendId", parsedFriendId.toString()), exception);
        }
    }

    @GET
    @Path("/{friendId}/location")
    @Operation(summary = "Get a friend's location",
            description = "Returns the latest GPS point of a friend. Requires the friend to share their live "
                    + "location with you.")
    public GpsPointPathPointDTO getFriendLocation(
            @Parameter(description = "User ID of the friend.")
            @PathParam("friendId") @NotNull String friendId) {
        UUID parsedFriendId = parseFriendId(friendId);
        try {
            GpsPointEntity location = friendService.getFriendLocation(
                    currentUserService.getCurrentUserId(), parsedFriendId);
            if (location == null) {
                throw new GeoPulseException(FRIEND_LOCATION_NOT_FOUND, "Friend location not found",
                        Map.of("friendId", parsedFriendId.toString()));
            }
            return gpsPointMapper.toPathPoint(location);
        } catch (NotAuthorizedUserException exception) {
            throw new GeoPulseException(FRIEND_LOCATION_ACCESS_DENIED, FRIEND_LOCATION_ACCESS_DENIED.title(),
                    Map.of("friendId", parsedFriendId.toString()), exception);
        }
    }

    @GET
    @Path("/trails")
    @Operation(summary = "Get friends' recent trails",
            description = "Returns the recent GPS points of every friend who shares their live location with you, "
                    + "for drawing their movement on a map.")
    public List<FriendLocationTrailDTO> getFriendsLocationTrails(
            @Parameter(description = "Length of the trail in minutes, from 1 to 1440. Defaults to 60.")
            @QueryParam("minutes") @DefaultValue("60") Integer minutes,
            @Parameter(description = "End of the trail, as an ISO-8601 instant. Defaults to now.")
            @QueryParam("to") String endTime) {
        if (minutes == null || minutes <= 0 || minutes > 1440) {
            throw new GeoPulseException(INVALID_FRIEND_TRAIL_RANGE, "minutes must be between 1 and 1440",
                    Map.of("min", 1, "max", 1440));
        }

        Instant requestedEndTime;
        try {
            requestedEndTime = endTime != null && !endTime.isBlank() ? Instant.parse(endTime) : Instant.now();
        } catch (DateTimeParseException exception) {
            throw new GeoPulseException(INVALID_FRIEND_TRAIL_RANGE, "endTime must use ISO-8601 format", exception);
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
    @Operation(summary = "Find users to invite",
            description = "Searches users on this server by email or full name, excluding yourself, existing "
                    + "friends, and users with a pending invitation. Returns at most 20 users.")
    public List<UserSearchDTO> searchUsersToInvite(
            @Parameter(description = "Part of an email address or full name.", example = "alex")
            @QueryParam("q") @NotNull String query) {
        return friendService.searchUsersToInvite(currentUserService.getCurrentUserId(), query);
    }

    @GET
    @Path("/{friendId}/permissions")
    @Operation(summary = "Get what you share with a friend",
            description = "Returns whether you share your timeline and your live location with a friend.")
    public UserFriendPermissionDTO getFriendPermissions(
            @Parameter(description = "User ID of the friend.")
            @PathParam("friendId") @NotNull String friendId) {
        UUID parsedFriendId = parseFriendId(friendId);
        try {
            return friendService.getFriendPermissions(currentUserService.getCurrentUserId(), parsedFriendId);
        } catch (FriendsException exception) {
            throw new GeoPulseException(FRIEND_RELATIONSHIP_NOT_FOUND, FRIEND_RELATIONSHIP_NOT_FOUND.title(), Map.of("friendId", parsedFriendId.toString()), exception);
        }
    }

    @PUT
    @Path("/{friendId}/permissions")
    @Transactional
    @Operation(summary = "Share your timeline with a friend",
            description = "Turns sharing of your full timeline with a friend on or off.")
    public UserFriendPermissionDTO updateFriendPermissions(
            @Parameter(description = "User ID of the friend.")
            @PathParam("friendId") @NotNull String friendId,
            @NotNull @Valid UpdateTimelinePermissionRequest request) {
        UUID parsedFriendId = parseFriendId(friendId);
        try {
            return friendService.updateFriendPermissions(
                    currentUserService.getCurrentUserId(), parsedFriendId, request.shareTimeline());
        } catch (FriendsException exception) {
            throw new GeoPulseException(FRIEND_RELATIONSHIP_NOT_FOUND, FRIEND_RELATIONSHIP_NOT_FOUND.title(), Map.of("friendId", parsedFriendId.toString()), exception);
        }
    }

    @GET
    @Path("/permissions")
    @Operation(summary = "List what you share with friends",
            description = "Returns your timeline and live-location sharing settings for every friend.")
    public List<UserFriendPermissionDTO> getAllFriendPermissions() {
        return friendService.getAllFriendPermissions(currentUserService.getCurrentUserId());
    }

    @PUT
    @Path("/{friendId}/permissions/live")
    @Transactional
    @Operation(summary = "Share your live location with a friend",
            description = "Turns sharing of your current location with a friend on or off.")
    public UserFriendPermissionDTO updateLiveLocationPermission(
            @Parameter(description = "User ID of the friend.")
            @PathParam("friendId") @NotNull String friendId,
            @NotNull @Valid UpdateLiveLocationPermissionRequest request) {
        UUID parsedFriendId = parseFriendId(friendId);
        try {
            return friendService.updateLiveLocationPermission(
                    currentUserService.getCurrentUserId(), parsedFriendId, request.shareLiveLocation());
        } catch (FriendsException exception) {
            throw new GeoPulseException(FRIEND_RELATIONSHIP_NOT_FOUND, FRIEND_RELATIONSHIP_NOT_FOUND.title(), Map.of("friendId", parsedFriendId.toString()), exception);
        }
    }

    private UUID parseFriendId(String friendId) {
        try {
            return UUID.fromString(friendId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new GeoPulseException(INVALID_FRIEND_ID, "Friend ID must be a UUID", exception);
        }
    }
}
