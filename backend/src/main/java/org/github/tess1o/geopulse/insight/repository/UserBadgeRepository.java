package org.github.tess1o.geopulse.insight.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.UserBadgeEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserBadgeRepository implements PanacheRepositoryBase<UserBadgeEntity, UUID> {

    /**
     * Find all badges for a specific user
     */
    public List<UserBadgeEntity> findByUserId(UUID userId) {
        return list("user.id = ?1", userId);
    }

    /**
     * Find all incomplete badges for a specific user (earned = false)
     */
    public List<UserBadgeEntity> findIncompleteBadgesByUserId(UUID userId) {
        return list("user.id = ?1 and earned = false", userId);
    }

    /**
     * Delete all badges for a specific user
     * Used when regenerating all badges for a user
     */
    public void deleteByUserId(UUID userId) {
        delete("user.id = ?1", userId);
    }

    /**
     * Check if user has any badges stored
     */
    public boolean existsByUserId(UUID userId) {
        return count("user.id = ?1", userId) > 0;
    }

    /**
     * Get all badge IDs for a specific user
     * Used to detect missing badge types for existing users
     */
    public List<String> findBadgeIdsByUserId(UUID userId) {
        return getEntityManager()
                .createQuery("SELECT ub.badgeId FROM UserBadgeEntity ub WHERE ub.user.id = :userId", String.class)
                .setParameter("userId", userId)
                .getResultList();
    }
}