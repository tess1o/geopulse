package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.UUID;

/**
 * Non-blocking access to a user's timeline for background jobs.
 * <p>
 * Timeline generation holds the user's row lock for its whole run (see
 * {@code StreamingTimelineGenerationService.acquireLock}). Background jobs must not queue behind a long import or
 * full rebuild, so they take the same lock without waiting and skip the user when it is held.
 */
@ApplicationScoped
public class TimelineUserLock {

    @Inject
    EntityManager entityManager;

    /**
     * Locks the user's row for the current transaction, or returns false at once if another transaction holds it.
     * FOR NO KEY UPDATE conflicts with generation's lock but not with saving points, which only need the row to exist.
     */
    public boolean tryLock(UUID userId) {
        return !entityManager.createNativeQuery("""
                        SELECT id FROM users WHERE id = ?1 FOR NO KEY UPDATE SKIP LOCKED
                        """)
                .setParameter(1, userId)
                .getResultList()
                .isEmpty();
    }
}
