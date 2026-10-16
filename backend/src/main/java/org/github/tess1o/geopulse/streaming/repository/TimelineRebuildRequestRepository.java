package org.github.tess1o.geopulse.streaming.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.streaming.model.TimelineRebuildRequest;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pending timeline rebuilds, one row per user (see {@code V36.73.0__Add_timeline_rebuild_requests.sql}).
 */
@ApplicationScoped
public class TimelineRebuildRequestRepository {

    private static final int MAX_ERROR_LENGTH = 2000;

    @Inject
    EntityManager entityManager;

    /**
     * Records that the user's timeline is out of date from {@code from} onwards, merging with any pending request.
     * Joins the caller's transaction, so the request commits together with the change that needs it.
     */
    @Transactional
    public void request(UUID userId, Instant from) {
        if (userId == null || from == null) {
            return;
        }
        entityManager.createNativeQuery("""
                INSERT INTO timeline_rebuild_requests (user_id, rebuild_from, revision, created_at, updated_at)
                VALUES (?1, ?2, 1, now(), now())
                ON CONFLICT (user_id) DO UPDATE
                SET rebuild_from = LEAST(timeline_rebuild_requests.rebuild_from, EXCLUDED.rebuild_from),
                    revision = timeline_rebuild_requests.revision + 1,
                    updated_at = now()
                """)
                .setParameter(1, userId)
                .setParameter(2, from)
                .executeUpdate();
    }

    /**
     * Like {@link #request} but commits on its own, for callers whose following work may fail and roll back
     * (an import's timeline generation, a job that could not be started).
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void requestIndependently(UUID userId, Instant from) {
        request(userId, from);
    }

    public Optional<TimelineRebuildRequest> find(UUID userId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT rebuild_from, revision, failed_attempts, last_failed_at
                FROM timeline_rebuild_requests
                WHERE user_id = ?1
                """)
                .setParameter(1, userId)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = rows.getFirst();
        return Optional.of(new TimelineRebuildRequest(
                userId,
                toInstant(row[0]),
                ((Number) row[1]).longValue(),
                ((Number) row[2]).intValue(),
                toInstant(row[3])));
    }

    /**
     * Clears the request a generation read when it started. Returns false, and keeps the request, when it changed
     * since: a point saved while the generation ran might not have been processed.
     */
    @Transactional
    public boolean complete(TimelineRebuildRequest request) {
        return entityManager.createNativeQuery("""
                DELETE FROM timeline_rebuild_requests
                WHERE user_id = ?1 AND revision = ?2
                """)
                .setParameter(1, request.userId())
                .setParameter(2, request.revision())
                .executeUpdate() > 0;
    }

    /**
     * Counts a failed generation of the pending request. Commits on its own because the generation's transaction is
     * rolling back. Never waits long for the row: a failure must not turn into a stuck job.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordFailure(UUID userId, String error) {
        entityManager.createNativeQuery("SET LOCAL lock_timeout = '5s'").executeUpdate();
        entityManager.createNativeQuery("""
                UPDATE timeline_rebuild_requests
                SET failed_attempts = failed_attempts + 1,
                    last_failed_at = now(),
                    last_error = ?2,
                    updated_at = now()
                WHERE user_id = ?1
                """)
                .setParameter(1, userId)
                .setParameter(2, truncate(error))
                .executeUpdate();
    }

    @Transactional
    public void delete(UUID userId) {
        entityManager.createNativeQuery("DELETE FROM timeline_rebuild_requests WHERE user_id = ?1")
                .setParameter(1, userId)
                .executeUpdate();
    }

    private static String truncate(String error) {
        if (error == null || error.length() <= MAX_ERROR_LENGTH) {
            return error;
        }
        return error.substring(0, MAX_ERROR_LENGTH);
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        return null;
    }
}
