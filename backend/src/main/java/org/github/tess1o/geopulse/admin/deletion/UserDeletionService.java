package org.github.tess1o.geopulse.admin.deletion;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.session.model.SessionRevocationReason;
import org.github.tess1o.geopulse.auth.session.service.UserSessionService;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.ADMIN_USER_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.USER_DELETION_IN_PROGRESS;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.USER_DELETION_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.USER_DELETION_NOT_RETRYABLE;

/**
 * Requests user deletions and answers whether a user is being deleted.
 * <p>
 * A request only records the deletion and locks the user out; {@link UserDeletionWorker} deletes the data. The
 * request never touches the user's row, so it does not wait for a timeline job that holds it.
 */
@ApplicationScoped
@Slf4j
public class UserDeletionService {

    /** Rows that give access to the user or the user's data, removed as soon as the deletion is requested. */
    private static final List<String> ACCESS_ROWS = List.of(
            "DELETE FROM user_api_tokens WHERE user_id = :userId",
            "DELETE FROM gps_source_config WHERE user_id = :userId",
            "DELETE FROM user_oidc_connections WHERE user_id = :userId",
            "DELETE FROM mobile_auth_codes WHERE user_id = :userId",
            "DELETE FROM shared_link WHERE user_id = :userId",
            "DELETE FROM user_friend_permissions WHERE user_id = :userId OR friend_id = :userId",
            "DELETE FROM user_friends WHERE user_id = :userId OR friend_id = :userId",
            "DELETE FROM friend_invitations WHERE sender_id = :userId OR receiver_id = :userId");

    private final EntityManager entityManager;
    private final UserRepository userRepository;
    private final AdminMembershipGuard adminGuard;
    private final UserSessionService userSessionService;
    private final UserDeletionRepository deletionRepository;

    @Inject
    public UserDeletionService(EntityManager entityManager,
                               UserRepository userRepository,
                               AdminMembershipGuard adminGuard,
                               UserSessionService userSessionService,
                               UserDeletionRepository deletionRepository) {
        this.entityManager = entityManager;
        this.userRepository = userRepository;
        this.adminGuard = adminGuard;
        this.userSessionService = userSessionService;
        this.deletionRepository = deletionRepository;
    }

    /**
     * Records the deletion and locks the user out: sessions, API tokens, GPS sources, OIDC links, shares and
     * friendships go at once. A repeated request returns the pending deletion.
     */
    @Transactional
    public UserDeletionResponse requestDeletion(UUID userId, UUID requestedBy) {
        // Also serializes concurrent requests for the same user.
        adminGuard.lock();
        UserDeletionEntity pending = UserDeletionEntity.findById(userId);
        if (pending != null && pending.getStatus().isPending()) {
            return UserDeletionResponse.of(pending);
        }
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new GeoPulseException(ADMIN_USER_NOT_FOUND, "User not found");
        }
        adminGuard.checkCanStopBeingAdmin(userId);

        if (pending != null) {
            pending.delete();
            entityManager.flush();
        }
        Instant now = Instant.now();
        UserDeletionEntity deletion = new UserDeletionEntity();
        deletion.setUserId(userId);
        deletion.setUserEmail(user.getEmail());
        deletion.setRequestedBy(requestedBy);
        deletion.setRequestedAt(now);
        deletion.setStatus(UserDeletionStatus.QUEUED);
        deletion.persist();

        userSessionService.revokeAllForUser(userId, null, SessionRevocationReason.USER_DELETED);
        for (String sql : ACCESS_ROWS) {
            entityManager.createNativeQuery(sql).setParameter("userId", userId).executeUpdate();
        }

        log.info("Deletion of user {} requested by {}", userId, requestedBy);
        return UserDeletionResponse.of(deletion);
    }

    /** Starts a failed deletion again where it stopped. */
    @Transactional
    public UserDeletionResponse retry(UUID userId) {
        // Locked like the worker's failure reports, so a retry and a report never overwrite each other.
        UserDeletionEntity deletion = entityManager.find(UserDeletionEntity.class, userId,
                LockModeType.PESSIMISTIC_WRITE);
        if (deletion == null) {
            throw new GeoPulseException(USER_DELETION_NOT_FOUND, "User deletion not found");
        }
        if (deletion.getStatus() != UserDeletionStatus.FAILED) {
            throw new GeoPulseException(USER_DELETION_NOT_RETRYABLE, "Only a failed deletion can be retried");
        }
        deletion.setStatus(deletion.getPhase() == null ? UserDeletionStatus.QUEUED : UserDeletionStatus.RUNNING);
        deletion.setAttempts(0);
        deletion.setRevision(deletion.getRevision() + 1);
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE user_deletions SET next_attempt_at = now() WHERE user_id = ?1")
                .setParameter(1, userId)
                .executeUpdate();
        log.info("Deletion of user {} retried", userId);
        return UserDeletionResponse.of(deletion);
    }

    @Transactional
    public Optional<UserDeletionResponse> find(UUID userId) {
        return UserDeletionEntity.<UserDeletionEntity>findByIdOptional(userId).map(UserDeletionResponse::of);
    }

    /** The pending deletions among {@code userIds}, by user. */
    @Transactional
    public Map<UUID, UserDeletionResponse> findPending(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return UserDeletionEntity.<UserDeletionEntity>list("userId in ?1 and status <> ?2", userIds,
                        UserDeletionStatus.COMPLETED).stream()
                .map(UserDeletionResponse::of)
                .collect(Collectors.toMap(UserDeletionResponse::getUserId, Function.identity()));
    }

    /** Whether the user is being deleted, including a deletion that failed and waits for a retry. */
    public boolean isPending(UUID userId) {
        return deletionRepository.isPending(userId);
    }

    /** Fails with {@code USER_DELETION_IN_PROGRESS} while the user is being deleted. */
    public void requireNotPending(UUID userId) {
        if (isPending(userId)) {
            throw new GeoPulseException(USER_DELETION_IN_PROGRESS, "The user is being deleted");
        }
    }
}
