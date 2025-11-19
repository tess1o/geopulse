package org.github.tess1o.geopulse.admin.service;

import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.user.service.SecurePasswordUtils;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for admin user management operations.
 */
@ApplicationScoped
@Slf4j
public class AdminUserService {

    private final UserRepository userRepository;
    private final SecurePasswordUtils passwordUtils;
    private final EntityManager entityManager;

    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int TEMP_PASSWORD_LENGTH = 12;

    @Inject
    public AdminUserService(UserRepository userRepository,
                            SecurePasswordUtils passwordUtils,
                            EntityManager entityManager) {
        this.userRepository = userRepository;
        this.passwordUtils = passwordUtils;
        this.entityManager = entityManager;
    }

    /**
     * Get paginated list of users with optional search.
     */
    public List<UserEntity> getUsers(String search, int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.descending(sortBy)
                : Sort.ascending(sortBy);

        if (search != null && !search.isBlank()) {
            String searchPattern = "%" + search.toLowerCase() + "%";
            return userRepository.find(
                    "lower(email) like ?1 or lower(fullName) like ?1",
                    sort,
                    searchPattern
            ).page(Page.of(page, size)).list();
        }

        return userRepository.findAll(sort)
                .page(Page.of(page, size))
                .list();
    }

    /**
     * Count users with optional search filter.
     */
    public long countUsers(String search) {
        if (search != null && !search.isBlank()) {
            String searchPattern = "%" + search.toLowerCase() + "%";
            return userRepository.count(
                    "lower(email) like ?1 or lower(fullName) like ?1",
                    searchPattern
            );
        }
        return userRepository.count();
    }

    /**
     * Get user by ID.
     */
    public Optional<UserEntity> getUserById(UUID userId) {
        return Optional.ofNullable(userRepository.findById(userId));
    }

    /**
     * Get GPS points count for a user.
     */
    public long getGpsPointsCount(UUID userId) {
        return (Long) entityManager.createQuery(
                "SELECT COUNT(g) FROM GpsPointEntity g WHERE g.user.id = :userId"
        ).setParameter("userId", userId).getSingleResult();
    }

    /**
     * Get linked OIDC providers for a user.
     */
    @SuppressWarnings("unchecked")
    public List<String> getLinkedOidcProviders(UUID userId) {
        return entityManager.createQuery(
                "SELECT c.providerName FROM UserOidcConnectionEntity c WHERE c.userId = :userId"
        ).setParameter("userId", userId).getResultList();
    }

    /**
     * Enable or disable a user account.
     */
    @Transactional
    public void setUserStatus(UUID userId, boolean active) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        user.setActive(active);
        log.info("User {} {} by admin", userId, active ? "enabled" : "disabled");
    }

    /**
     * Change user role.
     */
    @Transactional
    public void changeUserRole(UUID userId, Role newRole) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        // Check if this is the last admin trying to demote themselves
        if (user.getRole() == Role.ADMIN && newRole == Role.USER) {
            long adminCount = userRepository.count("role", Role.ADMIN);
            if (adminCount <= 1) {
                throw new IllegalStateException("Cannot demote the last admin user");
            }
        }

        Role oldRole = user.getRole();
        user.setRole(newRole);
        log.info("User {} role changed from {} to {}", userId, oldRole, newRole);
    }

    /**
     * Reset user password and return the temporary password.
     */
    @Transactional
    public String resetPassword(UUID userId) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        String tempPassword = generateTemporaryPassword();
        user.setPasswordHash(passwordUtils.hashPassword(tempPassword));

        log.info("Password reset for user {} by admin", userId);
        return tempPassword;
    }

    /**
     * Delete user and all associated data.
     */
    @Transactional
    public void deleteUser(UUID userId) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        // Check if trying to delete the last admin
        if (user.getRole() == Role.ADMIN) {
            long adminCount = userRepository.count("role", Role.ADMIN);
            if (adminCount <= 1) {
                throw new IllegalStateException("Cannot delete the last admin user");
            }
        }

        String email = user.getEmail();

        // Delete in order due to foreign key constraints
        // Note: Some entities have cascade delete, but we'll be explicit

        // Delete OIDC connections
        entityManager.createQuery("DELETE FROM UserOidcConnectionEntity c WHERE c.userId = :userId")
                .setParameter("userId", userId)
                .executeUpdate();

        // Delete audit logs (keep for compliance? or delete?)
        // For now, we'll keep audit logs but they'll reference a deleted user

        // Delete timeline data
        entityManager.createQuery("DELETE FROM TimelineTripEntity t WHERE t.userId = :userId")
                .setParameter("userId", userId)
                .executeUpdate();

        entityManager.createQuery("DELETE FROM TimelineStayEntity s WHERE s.userId = :userId")
                .setParameter("userId", userId)
                .executeUpdate();

        entityManager.createQuery("DELETE FROM TimelineDataGapEntity g WHERE g.userId = :userId")
                .setParameter("userId", userId)
                .executeUpdate();

        // Delete GPS points
        entityManager.createQuery("DELETE FROM GpsPointEntity g WHERE g.user.id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();

        // Delete GPS source configs
        entityManager.createQuery("DELETE FROM GpsSourceConfigEntity c WHERE c.user.id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();

        // Delete favorite locations
        entityManager.createQuery("DELETE FROM FavoriteLocationEntity f WHERE f.userId = :userId")
                .setParameter("userId", userId)
                .executeUpdate();

        // Delete shared links
        entityManager.createQuery("DELETE FROM SharedLinkEntity s WHERE s.user.id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();

        // Delete friend relationships
        entityManager.createQuery("DELETE FROM UserFriendEntity f WHERE f.user.id = :userId OR f.friend.id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();

        // Delete friend invitations
        entityManager.createQuery("DELETE FROM FriendInvitationEntity i WHERE i.sender.id = :userId OR i.receiver.id = :userId")
                .setParameter("userId", userId)
                .executeUpdate();

        // Delete badges
        entityManager.createQuery("DELETE FROM UserBadgeEntity b WHERE b.userId = :userId")
                .setParameter("userId", userId)
                .executeUpdate();

        // Finally delete the user
        userRepository.deleteById(userId);

        log.info("User {} ({}) deleted with all associated data", userId, email);
    }

    /**
     * Count admin users.
     */
    public long countAdmins() {
        return userRepository.count("role", Role.ADMIN);
    }

    private String generateTemporaryPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            password.append(TEMP_PASSWORD_CHARS.charAt(random.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return password.toString();
    }
}
