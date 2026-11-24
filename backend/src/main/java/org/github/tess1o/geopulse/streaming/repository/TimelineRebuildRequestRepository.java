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
                WITH requested AS (
                INSERT INTO timeline_rebuild_requests (user_id, rebuild_from, open_from, revision, created_at, updated_at)
                VALUES (?1, ?2, ?2, 1, now(), now())
                ON CONFLICT (user_id) DO UPDATE
                SET rebuild_from = LEAST(timeline_rebuild_requests.rebuild_from, EXCLUDED.rebuild_from),
                    open_from = LEAST(timeline_rebuild_requests.open_from, EXCLUDED.open_from),
                    revision = timeline_rebuild_requests.revision + 1,
                    updated_at = now()
                RETURNING user_id, revision
                )
                INSERT INTO timeline_rebuild_changes (user_id, revision, open_from)
                SELECT user_id, revision, ?2 FROM requested
                """)
                .setParameter(1, userId)
                .setParameter(2, from)
                .executeUpdate();
    }

    /**
     * Records that corrections changed the period [{@code from}, {@code to}]. No GPS point changed, so only that
     * period needs rebuilding; it merges with pending windows into one covering period. Joins the caller's
     * transaction.
     */
    @Transactional
    public void requestWindow(UUID userId, Instant from, Instant to) {
        if (userId == null || from == null || to == null) {
            return;
        }
        entityManager.createNativeQuery("""
                WITH requested AS (
                INSERT INTO timeline_rebuild_requests (user_id, rebuild_from, window_to, revision, created_at, updated_at)
                VALUES (?1, ?2, ?3, 1, now(), now())
                ON CONFLICT (user_id) DO UPDATE
                SET rebuild_from = LEAST(timeline_rebuild_requests.rebuild_from, EXCLUDED.rebuild_from),
                    window_to = GREATEST(timeline_rebuild_requests.window_to, EXCLUDED.window_to),
                    revision = timeline_rebuild_requests.revision + 1,
                    updated_at = now()
                RETURNING user_id, revision
                )
                INSERT INTO timeline_rebuild_changes (user_id, revision, window_from, window_to)
                SELECT user_id, revision, ?2, ?3 FROM requested
                """)
                .setParameter(1, userId)
                .setParameter(2, from)
                .setParameter(3, to)
                .executeUpdate();
    }

    /**
     * Clears correction changes through the revision a window rebuild read, keeping open-ended changes and any
     * later corrections for the next pass.
     */
    @Transactional
    public boolean completeWindow(TimelineRebuildRequest request) {
        return complete(request, true);
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
                SELECT rebuild_from, revision, failed_attempts, last_failed_at, open_from, window_to
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
                toInstant(row[3]),
                toInstant(row[4]),
                toInstant(row[5])));
    }

    /**
     * Retires changes through the revision a generation read. Changes saved during generation remain pending at
     * their own earliest timestamp, rather than repeatedly rebuilding the range that already completed.
     */
    @Transactional
    public boolean complete(TimelineRebuildRequest request) {
        return complete(request, false);
    }

    private boolean complete(TimelineRebuildRequest request, boolean windowOnly) {
        // Writers lock this summary before appending a change. Take the same lock only at completion, so the
        // remaining ranges cannot change while they are summarized and GPS ingestion stays free during generation.
        if (entityManager.createNativeQuery("""
                SELECT user_id FROM timeline_rebuild_requests WHERE user_id = ?1 FOR UPDATE
                """)
                .setParameter(1, request.userId())
                .getResultList().isEmpty()) {
            return false;
        }
        if (windowOnly) {
            entityManager.createNativeQuery("""
                    UPDATE timeline_rebuild_changes SET window_from = NULL, window_to = NULL
                    WHERE user_id = ?1 AND revision <= ?2
                    """)
                    .setParameter(1, request.userId())
                    .setParameter(2, request.revision())
                    .executeUpdate();
        }
        entityManager.createNativeQuery(windowOnly ? """
                DELETE FROM timeline_rebuild_changes
                WHERE user_id = ?1 AND revision <= ?2 AND open_from IS NULL
                """ : """
                DELETE FROM timeline_rebuild_changes WHERE user_id = ?1 AND revision <= ?2
                """)
                .setParameter(1, request.userId())
                .setParameter(2, request.revision())
                .executeUpdate();
        entityManager.createNativeQuery("""
                UPDATE timeline_rebuild_requests r
                SET rebuild_from = LEAST(p.open_from, p.window_from), open_from = p.open_from,
                    window_to = p.window_to, revision = r.revision + 1,
                    failed_attempts = 0, last_failed_at = NULL, last_error = NULL, updated_at = now()
                FROM (SELECT MIN(open_from) AS open_from, MIN(window_from) AS window_from,
                             MAX(window_to) AS window_to
                      FROM timeline_rebuild_changes WHERE user_id = ?1) p
                WHERE r.user_id = ?1 AND (p.open_from IS NOT NULL OR p.window_from IS NOT NULL)
                """)
                .setParameter(1, request.userId())
                .executeUpdate();
        entityManager.createNativeQuery("""
                DELETE FROM timeline_rebuild_requests
                WHERE user_id = ?1 AND NOT EXISTS (
                    SELECT 1 FROM timeline_rebuild_changes WHERE user_id = ?1)
                """)
                .setParameter(1, request.userId())
                .executeUpdate();
        return true;
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
