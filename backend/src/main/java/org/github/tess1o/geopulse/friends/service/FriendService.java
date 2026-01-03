package org.github.tess1o.geopulse.friends.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.friends.exceptions.FriendsException;
import org.github.tess1o.geopulse.friends.invitation.repository.FriendInvitationRepository;
import org.github.tess1o.geopulse.friends.model.FriendInfoDTO;
import org.github.tess1o.geopulse.friends.model.UserFriendPermissionDTO;
import org.github.tess1o.geopulse.friends.model.UserFriendPermissionEntity;
import org.github.tess1o.geopulse.friends.repository.FriendshipRepository;
import org.github.tess1o.geopulse.friends.repository.UserFriendPermissionRepository;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.service.LocationPointResolver;
import org.github.tess1o.geopulse.user.exceptions.NotAuthorizedUserException;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.model.UserSearchDTO;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.locationtech.jts.geom.Point;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class FriendService {

    private final GpsPointRepository gpsPointRepository;
    private final LocationPointResolver locationPointResolver;
    private final FriendshipRepository friendshipRepository;
    private final FriendInvitationRepository friendInvitationRepository;
    private final UserRepository userRepository;
    private final UserFriendPermissionRepository permissionRepository;

    @Inject
    public FriendService(
            GpsPointRepository gpsPointRepository,
            LocationPointResolver locationPointResolver,
            FriendshipRepository friendshipRepository,
            FriendInvitationRepository friendInvitationRepository,
            UserRepository userRepository,
            UserFriendPermissionRepository permissionRepository) {
        this.gpsPointRepository = gpsPointRepository;
        this.locationPointResolver = locationPointResolver;
        this.friendshipRepository = friendshipRepository;
        this.friendInvitationRepository = friendInvitationRepository;
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
    }

    /**
     * Remove a friend.
     *
     * @param userId   The ID of the user removing the friend
     * @param friendId The ID of the friend to remove
     * @throws FriendsException if the friend cannot be removed
     */
    @Transactional
    public void removeFriend(UUID userId, UUID friendId) {
        // Check if they are friends
        if (!friendshipRepository.existsFriendship(userId, friendId)) {
            throw new FriendsException("Not friends with user: " + friendId);
        }

        // Delete the friendship (both directions)
        long deletedCount = friendshipRepository.deleteFriendship(userId, friendId);
        log.info("Friendship removed between {} and {}, deleted {} records", userId, friendId, deletedCount);
    }

    /**
     * Get the most recent location of a friend.
     *
     * @param userId   The ID of the user requesting the location
     * @param friendId The ID of the friend
     * @return The most recent location entity of the friend
     * @throws NotAuthorizedUserException if the location cannot be retrieved
     */
    public GpsPointEntity getFriendLocation(UUID userId, UUID friendId) {
        // Check if they are friends
        if (!friendshipRepository.existsFriendship(userId, friendId)) {
            throw new NotAuthorizedUserException("Not authorized to view location of user: " + friendId);
        }

        // Check if the friend has enabled location sharing
        UserEntity friend = userRepository.findById(friendId);
        if (friend == null || !friend.isShareLocationWithFriends()) {
            throw new NotAuthorizedUserException("Friend has disabled location sharing");
        }

        return gpsPointRepository.findByUserIdLatestGpsPoint(friendId);
    }


    public List<FriendInfoDTO> getAllFriends(UUID userId) {
        List<FriendInfoDTO> friends = friendshipRepository.findFriends(userId);

        friends.forEach(f -> {
            if (f.getLastLongitude() == null || f.getLastLatitude() == null) {
                f.setLastLocation("N/A");
            } else {
                Point lastLocation = GeoUtils.createPoint(f.getLastLongitude(), f.getLastLatitude());
                f.setLastLocation(locationPointResolver.resolveLocationWithReferences(f.getUserId(), lastLocation).getLocationName());
            }
        });
        return friends;
    }

    /**
     * Search for users to invite as friends.
     * This method uses a single optimized SQL query to exclude:
     * - The current user
     * - Existing friends
     * - Users with pending invitations (sent or received)
     *
     * @param currentUserId The ID of the current user
     * @param query The search query (email or full name)
     * @return List of users matching the search criteria (max 20)
     */
    public List<UserSearchDTO> searchUsersToInvite(UUID currentUserId, String query) {
        return userRepository.searchUsersToInvite(currentUserId, query);
    }

    /**
     * Get timeline sharing permissions for a specific friend.
     *
     * @param userId   The current user ID
     * @param friendId The friend ID
     * @return Permission DTO
     */
    public UserFriendPermissionDTO getFriendPermissions(UUID userId, UUID friendId) {
        // Check friendship exists
        if (!friendshipRepository.existsFriendship(userId, friendId)) {
            throw new FriendsException("Not friends with user: " + friendId);
        }

        // Get or create permission record
        Optional<UserFriendPermissionEntity> permission = permissionRepository.findByUserIdAndFriendId(userId, friendId);

        UserEntity user = userRepository.findById(userId);

        if (permission.isPresent()) {
            UserFriendPermissionEntity p = permission.get();
            return UserFriendPermissionDTO.builder()
                    .userId(userId)
                    .friendId(friendId)
                    .shareTimeline(p.getShareTimeline())
                    .shareLocationLive(user != null ? user.isShareLocationWithFriends() : false)
                    .build();
        } else {
            // Return default permissions (false)
            return UserFriendPermissionDTO.builder()
                    .userId(userId)
                    .friendId(friendId)
                    .shareTimeline(false)
                    .shareLocationLive(user != null ? user.isShareLocationWithFriends() : false)
                    .build();
        }
    }

    /**
     * Update timeline sharing permission for a friend.
     *
     * @param userId        The current user ID
     * @param friendId      The friend ID
     * @param shareTimeline Whether to allow timeline access
     * @return Updated permission DTO
     */
    @Transactional
    public UserFriendPermissionDTO updateFriendPermissions(UUID userId, UUID friendId, boolean shareTimeline) {
        // Check friendship exists
        if (!friendshipRepository.existsFriendship(userId, friendId)) {
            throw new FriendsException("Not friends with user: " + friendId);
        }

        UserEntity user = userRepository.findById(userId);

        // Get or create permission record
        Optional<UserFriendPermissionEntity> existingPermission = permissionRepository.findByUserIdAndFriendId(userId, friendId);

        if (existingPermission.isPresent()) {
            // Update existing
            permissionRepository.updateShareTimeline(userId, friendId, shareTimeline);
        } else {
            // Create new permission record
            UserEntity userEntity = userRepository.findById(userId);
            UserEntity friendEntity = userRepository.findById(friendId);

            if (userEntity == null || friendEntity == null) {
                throw new FriendsException("User or friend not found");
            }

            permissionRepository.createDefaultPermissions(userEntity, friendEntity);
            permissionRepository.updateShareTimeline(userId, friendId, shareTimeline);
        }

        log.info("Updated timeline permission for user {} -> friend {}: shareTimeline={}", userId, friendId, shareTimeline);

        return UserFriendPermissionDTO.builder()
                .userId(userId)
                .friendId(friendId)
                .shareTimeline(shareTimeline)
                .shareLocationLive(user != null ? user.isShareLocationWithFriends() : false)
                .build();
    }

    /**
     * Get all permissions for the current user (permissions they have granted).
     *
     * @param userId The current user ID
     * @return List of permission DTOs
     */
    public List<UserFriendPermissionDTO> getAllFriendPermissions(UUID userId) {
        List<UserFriendPermissionEntity> permissions = permissionRepository.findAllByUserId(userId);
        UserEntity user = userRepository.findById(userId);
        boolean shareLocationLive = user != null ? user.isShareLocationWithFriends() : false;

        return permissions.stream()
                .map(p -> UserFriendPermissionDTO.builder()
                        .userId(userId)
                        .friendId(p.getFriend().getId())
                        .shareTimeline(p.getShareTimeline())
                        .shareLocationLive(shareLocationLive)
                        .build())
                .toList();
    }

    /**
     * Check if a user can view a friend's timeline.
     *
     * @param userId   The user who owns the timeline
     * @param friendId The friend requesting access
     * @return true if permission granted, false otherwise
     */
    public boolean canViewFriendTimeline(UUID userId, UUID friendId) {
        if (!friendshipRepository.existsFriendship(userId, friendId)) {
            return false;
        }
        return permissionRepository.hasTimelinePermission(userId, friendId);
    }
}
