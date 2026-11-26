package org.github.tess1o.geopulse.admin.deletion;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import java.util.List;
import java.util.UUID;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.ADMIN_USER_UPDATE_INVALID;

/**
 * Keeps at least one active administrator. Demoting, disabling and deleting a user all check here, one at a time:
 * without the lock, two admins removing each other at the same moment would both see the other one remain.
 */
@ApplicationScoped
public class AdminMembershipGuard {

    /** Transaction-scoped advisory lock taken by every check; arbitrary but fixed. */
    public static final long LOCK_KEY = 0x4750_4164_6d69_6e73L;

    private final EntityManager entityManager;

    @Inject
    public AdminMembershipGuard(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Fails unless at least one other active administrator, not being deleted, remains after {@code userId} stops
     * being one. Holds the lock until the caller's transaction ends, so the caller's change is in place before the
     * next check runs.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public void checkCanStopBeingAdmin(UUID userId) {
        lock();

        List<?> activeAdmin = entityManager.createNativeQuery("""
                        SELECT id FROM users WHERE id = ?1 AND role = 'ADMIN' AND is_active
                        """)
                .setParameter(1, userId)
                .getResultList();
        if (activeAdmin.isEmpty()) {
            return;
        }

        Number others = (Number) entityManager.createNativeQuery("""
                        SELECT count(*) FROM users u
                        WHERE u.role = 'ADMIN' AND u.is_active AND u.id <> ?1
                          AND NOT EXISTS (SELECT 1 FROM user_deletions d
                                          WHERE d.user_id = u.id AND d.status <> 'COMPLETED')
                        """)
                .setParameter(1, userId)
                .getSingleResult();
        if (others.longValue() < 1) {
            throw new GeoPulseException(ADMIN_USER_UPDATE_INVALID, "At least one active administrator must remain");
        }
    }

    /** Takes the guard's lock for the rest of the transaction. Taking it again in the same transaction is free. */
    @Transactional(Transactional.TxType.MANDATORY)
    public void lock() {
        // The function returns void, which has no Java mapping; select a constant instead.
        entityManager.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(?1)")
                .setParameter(1, LOCK_KEY)
                .getSingleResult();
    }
}
