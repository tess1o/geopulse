package org.github.tess1o.geopulse.friends.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import org.github.tess1o.geopulse.friends.model.UserFriendPermissionEntity;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing friend timeline sharing permissions.
 */
@ApplicationScoped
public class UserFriendPermissionRepository implements PanacheRepository<UserFriendPermissionEntity> {

    private final EntityManager entityManager;

    @Inject
    public UserFriendPermissionRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Find permission record between two users.
     *
     * @param userId   The user granting permission
     * @param friendId The friend receiving permission
     * @return Optional permission entity
     */
    public Optional<UserFriendPermissionEntity> findByUserIdAndFriendId(UUID userId, UUID friendId) {
        try {
            UserFriendPermissionEntity permission = find("user.id = ?1 AND friend.id = ?2", userId, friendId)
                    .singleResult();
            return Optional.of(permission);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * Get all friends who have granted timeline permission to the current user.
     * Returns UUIDs of users whose timeline the current user can view.
     *
     * @param currentUserId The user requesting access
     * @return List of user IDs who have granted permission
     */
    public List<UUID> findFriendsWhoSharedTimelineWithUser(UUID currentUserId) {
        String query = """
                SELECT p.user.id
                FROM UserFriendPermissionEntity p
                WHERE p.friend.id = :currentUserId
                AND p.shareTimeline = true
                """;

        return entityManager.createQuery(query, UUID.class)
                .setParameter("currentUserId", currentUserId)
                .getResultList();
    }

    /**
     * Create default permission record for a new friendship.
     * By default, timeline sharing is disabled (opt-in).
     *
     * @param user   The user entity
     * @param friend The friend entity
     */
    public void createDefaultPermissions(UserEntity user, UserEntity friend) {
        UserFriendPermissionEntity permission = new UserFriendPermissionEntity();
        permission.setUser(user);
        permission.setFriend(friend);
        permission.setShareTimeline(false);
        persist(permission);
    }

    /**
     * Update timeline sharing permission.
     *
     * @param userId        The user granting permission
     * @param friendId      The friend receiving permission
     * @param shareTimeline Whether to allow timeline access
     * @return Updated permission entity
     */
    public Optional<UserFriendPermissionEntity> updateShareTimeline(UUID userId, UUID friendId, boolean shareTimeline) {
        Optional<UserFriendPermissionEntity> existingPermission = findByUserIdAndFriendId(userId, friendId);

        if (existingPermission.isPresent()) {
            UserFriendPermissionEntity permission = existingPermission.get();
            permission.setShareTimeline(shareTimeline);
            persist(permission);
            return Optional.of(permission);
        }

        return Optional.empty();
    }

    /**
     * Check if a user has granted timeline access to a friend.
     *
     * @param userId   The user who owns the timeline
     * @param friendId The friend requesting access
     * @return true if permission is granted, false otherwise
     */
    public boolean hasTimelinePermission(UUID userId, UUID friendId) {
        Optional<UserFriendPermissionEntity> permission = findByUserIdAndFriendId(userId, friendId);
        return permission.map(UserFriendPermissionEntity::getShareTimeline).orElse(false);
    }

    /**
     * Check if a user has granted live location access to a friend.
     *
     * @param userId   The user who owns the location
     * @param friendId The friend requesting access
     * @return true if permission is granted, false otherwise
     */
    public boolean hasLiveLocationPermission(UUID userId, UUID friendId) {
        Optional<UserFriendPermissionEntity> permission = findByUserIdAndFriendId(userId, friendId);
        return permission.map(UserFriendPermissionEntity::getShareLiveLocation).orElse(false);
    }

    /**
     * Update live location sharing permission.
     *
     * @param userId             The user granting permission
     * @param friendId           The friend receiving permission
     * @param shareLiveLocation  Whether to allow live location access
     * @return Updated permission entity
     */
    public Optional<UserFriendPermissionEntity> updateShareLiveLocation(UUID userId, UUID friendId, boolean shareLiveLocation) {
        Optional<UserFriendPermissionEntity> existingPermission = findByUserIdAndFriendId(userId, friendId);

        if (existingPermission.isPresent()) {
            UserFriendPermissionEntity permission = existingPermission.get();
            permission.setShareLiveLocation(shareLiveLocation);
            persist(permission);
            return Optional.of(permission);
        }

        return Optional.empty();
    }

    /**
     * Get all permissions for a specific user (permissions they have granted to friends).
     *
     * @param userId The user ID
     * @return List of permission entities
     */
    public List<UserFriendPermissionEntity> findAllByUserId(UUID userId) {
        return list("user.id", userId);
    }

    /**
     * Delete permissions when friendship is removed (handled automatically by CASCADE).
     * This method is for explicit deletion if needed.
     *
     * @param userId   The first user
     * @param friendId The second user
     * @return Number of deleted records
     */
    public long deletePermissions(UUID userId, UUID friendId) {
        long count1 = delete("user.id = ?1 AND friend.id = ?2", userId, friendId);
        long count2 = delete("user.id = ?1 AND friend.id = ?2", friendId, userId);
        return count1 + count2;
    }
}
