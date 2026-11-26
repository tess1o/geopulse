package org.github.tess1o.geopulse.admin.deletion;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.UUID;

/**
 * Whether a user is being deleted. It has no other dependencies, so authentication can ask it on every request
 * without depending on the deletion service (which itself revokes sessions).
 */
@ApplicationScoped
public class UserDeletionRepository {

    private final EntityManager entityManager;

    @Inject
    public UserDeletionRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** Whether the user is being deleted, including a deletion that failed and waits for a retry. */
    public boolean isPending(UUID userId) {
        return !entityManager.createNativeQuery(
                        "SELECT 1 FROM user_deletions WHERE user_id = ?1 AND status <> 'COMPLETED'")
                .setParameter(1, userId)
                .getResultList()
                .isEmpty();
    }
}
