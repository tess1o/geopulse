package org.github.tess1o.geopulse.admin.deletion;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.TargetType;
import org.github.tess1o.geopulse.admin.service.AuditLogService;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One unit of deletion work per transaction. The deletion's row is locked with {@code FOR UPDATE SKIP LOCKED} for the
 * length of the step, so two workers never work on the same deletion, and a crash rolls back the batch and releases
 * the row with nothing else to clean up.
 */
@ApplicationScoped
@Slf4j
public class UserDeletionSteps {

    /** What is left of the user once the batched phases are done, deleted while the user's row is locked. */
    private static final List<String> FINAL_ROWS = List.of(
            "DELETE FROM geofence_rule_state WHERE subject_user_id = :userId",
            "DELETE FROM favorite_locations WHERE user_id = :userId",
            "DELETE FROM coverage_state WHERE user_id = :userId",
            "DELETE FROM user_api_tokens WHERE user_id = :userId",
            "DELETE FROM gps_source_config WHERE user_id = :userId",
            "DELETE FROM shared_link WHERE user_id = :userId",
            "DELETE FROM user_friends WHERE user_id = :userId OR friend_id = :userId",
            "DELETE FROM friend_invitations WHERE sender_id = :userId OR receiver_id = :userId",
            // used_by is nullable, so invitations this user accepted are kept.
            "UPDATE user_invitations SET used_by = NULL WHERE used_by = :userId",
            "DELETE FROM user_invitations WHERE created_by = :userId",
            // Everything else that references the user cascades.
            "DELETE FROM users WHERE id = :userId");

    private final EntityManager entityManager;
    private final AuditLogService auditLogService;

    @ConfigProperty(name = "geopulse.user-deletion.batch-size", defaultValue = "10000")
    int batchSize;

    @ConfigProperty(name = "geopulse.user-deletion.busy-user-delay", defaultValue = "5s")
    Duration busyUserDelay;

    @Inject
    public UserDeletionSteps(EntityManager entityManager, AuditLogService auditLogService) {
        this.entityManager = entityManager;
        this.auditLogService = auditLogService;
    }

    /**
     * Claims the next due deletion and does one step of it. Returns false when there is nothing due.
     *
     * @throws StepFailedException when the step failed; its transaction is rolled back
     */
    @Transactional
    public boolean runNext() {
        List<?> claimed = entityManager.createNativeQuery("""
                        SELECT user_id FROM user_deletions
                        WHERE status IN ('QUEUED', 'RUNNING') AND next_attempt_at <= now()
                        ORDER BY requested_at
                        LIMIT 1
                        FOR UPDATE SKIP LOCKED
                        """)
                .getResultList();
        if (claimed.isEmpty()) {
            return false;
        }
        UUID userId = (UUID) claimed.get(0);
        UserDeletionEntity deletion = entityManager.find(UserDeletionEntity.class, userId);
        long revision = deletion.getRevision();
        try {
            if (deletion.getPhase() == null) {
                prepare(deletion);
            } else if (deletion.getPhase() == UserDeletionPhase.FINALIZE) {
                finish(deletion);
            } else {
                deleteBatch(deletion);
            }
            deletion.setRevision(revision + 1);
            entityManager.flush();
        } catch (RuntimeException e) {
            throw new StepFailedException(userId, revision, e);
        }
        return true;
    }

    /**
     * Records a failed step: the deletion is tried again later, and fails for good after {@code maxAttempts} steps in
     * a row have failed. A failed deletion keeps the user locked out.
     * <p>
     * The step's transaction is already rolled back, so another worker may have claimed the deletion and made progress
     * in the meantime. The row is locked before it is read, and the report is dropped unless the deletion is still at
     * {@code failedRevision}, the revision the failed step saw.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordFailure(UUID userId, long failedRevision, String error, int maxAttempts, Duration retryDelay) {
        UserDeletionEntity deletion = entityManager.find(UserDeletionEntity.class, userId,
                LockModeType.PESSIMISTIC_WRITE);
        if (deletion == null || deletion.getRevision() != failedRevision || !deletion.getStatus().isWorkable()) {
            log.debug("Dropping a failure report for the deletion of user {}: it has changed since", userId);
            return;
        }
        deletion.setRevision(failedRevision + 1);
        int attempts = deletion.getAttempts() + 1;
        deletion.setAttempts(attempts);
        deletion.setLastError(error);
        retryAfter(userId, retryDelay.multipliedBy(attempts));
        if (attempts >= maxAttempts) {
            deletion.setStatus(UserDeletionStatus.FAILED);
            log.error("Deletion of user {} failed {} times and stopped; an admin can retry it. Last error: {}",
                    userId, attempts, error);
        } else {
            log.warn("Deletion of user {} failed (attempt {} of {}), retrying later: {}",
                    userId, attempts, maxAttempts, error);
        }
    }

    /**
     * Takes the user away from timeline processing. A timeline job holds the user's row while it runs; rather than
     * wait for it here, the deletion is tried again shortly.
     */
    private void prepare(UserDeletionEntity deletion) {
        UUID userId = deletion.getUserId();
        List<?> locked = entityManager.createNativeQuery("SELECT id FROM users WHERE id = ?1 FOR UPDATE SKIP LOCKED")
                .setParameter(1, userId)
                .getResultList();
        if (locked.isEmpty()) {
            boolean exists = !entityManager.createNativeQuery("SELECT id FROM users WHERE id = ?1")
                    .setParameter(1, userId)
                    .getResultList()
                    .isEmpty();
            if (exists) {
                log.debug("User {} is busy with a timeline job; deletion waits", userId);
                retryAfter(userId, busyUserDelay);
                return;
            }
            complete(deletion);
            return;
        }
        entityManager.createNativeQuery(
                        "UPDATE users SET is_active = false, timeline_status = 'DELETING' WHERE id = ?1")
                .setParameter(1, userId)
                .executeUpdate();
        deletion.setStatus(UserDeletionStatus.RUNNING);
        deletion.setPhase(UserDeletionPhase.first());
        log.info("Deleting user {}: locked out, deleting data", userId);
    }

    private void deleteBatch(UserDeletionEntity deletion) {
        UserDeletionPhase phase = deletion.getPhase();
        int deleted = entityManager.createNativeQuery(phase.batchSql())
                .setParameter("userId", deletion.getUserId())
                .setParameter("limit", batchSize)
                .executeUpdate();
        if (phase == UserDeletionPhase.GPS_POINTS) {
            deletion.setDeletedGpsPoints(deletion.getDeletedGpsPoints() + deleted);
        }
        if (deleted == 0) {
            deletion.setPhase(phase.next());
            log.info("Deleting user {}: {} done", deletion.getUserId(), phase);
        }
        deletion.setAttempts(0);
        deletion.setLastError(null);
    }

    /**
     * Deletes the user. The user's row is locked first: writers that were still adding rows (an import's last batch)
     * finish before, and new ones wait and then fail because the user is gone. What they added is swept here.
     */
    private void finish(UserDeletionEntity deletion) {
        UUID userId = deletion.getUserId();
        boolean exists = !entityManager.createNativeQuery("SELECT id FROM users WHERE id = ?1 FOR UPDATE")
                .setParameter(1, userId)
                .getResultList()
                .isEmpty();
        if (exists) {
            for (UserDeletionPhase phase : UserDeletionPhase.values()) {
                if (!phase.deletesRows()) {
                    continue;
                }
                int deleted = entityManager.createNativeQuery(phase.sweepSql())
                        .setParameter("userId", userId)
                        .executeUpdate();
                if (phase == UserDeletionPhase.GPS_POINTS) {
                    deletion.setDeletedGpsPoints(deletion.getDeletedGpsPoints() + deleted);
                }
            }
            for (String sql : FINAL_ROWS) {
                entityManager.createNativeQuery(sql).setParameter("userId", userId).executeUpdate();
            }
        }
        complete(deletion);
    }

    private void complete(UserDeletionEntity deletion) {
        UUID userId = deletion.getUserId();
        deletion.setStatus(UserDeletionStatus.COMPLETED);
        deletion.setPhase(UserDeletionPhase.FINALIZE);
        deletion.setCompletedAt(Instant.now());
        deletion.setAttempts(0);
        deletion.setLastError(null);

        UUID requestedBy = deletion.getRequestedBy();
        boolean requesterExists = requestedBy != null && !entityManager.createNativeQuery(
                        "SELECT id FROM users WHERE id = ?1")
                .setParameter(1, requestedBy)
                .getResultList()
                .isEmpty();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("email", deletion.getUserEmail());
        details.put("deletedGpsPoints", deletion.getDeletedGpsPoints());
        auditLogService.logAction(requesterExists ? requestedBy : null, ActionType.USER_DELETED, TargetType.USER,
                userId.toString(), details, null);
        log.info("User {} deleted with all associated data ({} GPS points)", userId, deletion.getDeletedGpsPoints());
    }

    /** Makes the deletion due again after {@code delay}, measured on the database clock the claim uses. */
    private void retryAfter(UUID userId, Duration delay) {
        entityManager.createNativeQuery("""
                        UPDATE user_deletions SET next_attempt_at = now() + make_interval(secs => ?2)
                        WHERE user_id = ?1
                        """)
                .setParameter(1, userId)
                .setParameter(2, (double) delay.toMillis() / 1000)
                .executeUpdate();
    }

    /** A step that failed; its transaction was rolled back. */
    public static class StepFailedException extends RuntimeException {
        private final UUID userId;
        private final long revision;

        StepFailedException(UUID userId, long revision, Throwable cause) {
            super("Deletion step for user " + userId + " failed: " + cause.getMessage(), cause);
            this.userId = userId;
            this.revision = revision;
        }

        public UUID userId() {
            return userId;
        }

        /** The revision of the deletion when the failed step claimed it. */
        public long revision() {
            return revision;
        }
    }
}
