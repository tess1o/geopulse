package org.github.tess1o.geopulse.admin.service;

import org.github.tess1o.geopulse.auth.session.model.SessionRevocationReason;
import org.github.tess1o.geopulse.auth.session.service.UserSessionService;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.deletion.AdminMembershipGuard;
import org.github.tess1o.geopulse.admin.deletion.UserDeletionService;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.shared.security.CryptoUtils;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.user.service.SecurePasswordUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
    UserSessionService userSessionService;

    @Inject
    AdminMembershipGuard adminGuard;

    @Inject
    UserDeletionService userDeletionService;

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
        // Map frontend field names to entity field names
        String mappedSortBy = mapSortField(sortBy);

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.descending(mappedSortBy)
                : Sort.ascending(mappedSortBy);

        if (search != null && !search.isBlank()) {
            String searchPattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
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
     * Map frontend field names to entity field names.
     * This handles cases where the frontend uses different naming conventions.
     */
    private String mapSortField(String field) {
        return switch (field) {
            case "active" -> "isActive";  // Frontend sends "active", entity uses "isActive"
            default -> field;
        };
    }

    /**
     * Count users with optional search filter.
     */
    public long countUsers(String search) {
        if (search != null && !search.isBlank()) {
            String searchPattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
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
     * Get the timestamp of the last GPS point for a user.
     */
    public Instant getLastGpsPointTimestamp(UUID userId) {
        try {
            return (Instant) entityManager.createQuery(
                    "SELECT MAX(g.timestamp) FROM GpsPointEntity g WHERE g.user.id = :userId"
            ).setParameter("userId", userId).getSingleResult();
        } catch (Exception e) {
            return null;
        }
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
        userDeletionService.requireNotPending(userId);
        if (!active) {
            adminGuard.checkCanStopBeingAdmin(userId);
        }

        user.setActive(active);
        if (!active) {
            userSessionService.revokeAllForUser(userId, null, SessionRevocationReason.USER_DISABLED);
        }
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

        userDeletionService.requireNotPending(userId);
        if (newRole != Role.ADMIN) {
            adminGuard.checkCanStopBeingAdmin(userId);
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
        userDeletionService.requireNotPending(userId);

        String tempPassword = generateTemporaryPassword();
        user.setPasswordHash(passwordUtils.hashPassword(tempPassword));
        userSessionService.revokeAllForUser(userId, null, SessionRevocationReason.PASSWORD_RESET);

        log.info("Password reset for user {} by admin", userId);
        return tempPassword;
    }

    private String generateTemporaryPassword() {
        return CryptoUtils.randomString(TEMP_PASSWORD_CHARS, TEMP_PASSWORD_LENGTH);
    }
}
