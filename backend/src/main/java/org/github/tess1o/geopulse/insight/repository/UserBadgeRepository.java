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
     * Find a specific badge for a user
     */
    public Optional<UserBadgeEntity> findByUserIdAndBadgeId(UUID userId, String badgeId) {
        return find("user.id = ?1 and badgeId = ?2", userId, badgeId).firstResultOptional();
    }

    /**
     * Find all incomplete badges for a specific user (earned = false)
     */
    public List<UserBadgeEntity> findIncompleteBadgesByUserId(UUID userId) {
        return list("user.id = ?1 and earned = false", userId);
    }

    /**
     * Find all users that have at least one incomplete badge
     * Used by scheduled job to efficiently target only users who need badge updates
     */
    public List<UUID> findUsersWithIncompleteBadges() {
        return getEntityManager()
                .createQuery("SELECT DISTINCT ub.user.id FROM UserBadgeEntity ub WHERE ub.earned = false", UUID.class)
                .getResultList();
    }

    /**
     * Find all incomplete badges across all users
     * Used by scheduled job to batch process incomplete badges
     */
    public List<UserBadgeEntity> findAllIncompleteBadges() {
        return list("earned = false");
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
     * Update badge as earned
     */
    public void updateBadgeAsEarned(UUID userId, String badgeId) {
        update("earned = true, lastCalculated = NOW() WHERE user.id = ?1 and badgeId = ?2", userId, badgeId);
    }

    /**
     * Get count of badges for a user
     */
    public long countByUserId(UUID userId) {
        return count("user.id = ?1", userId);
    }

    /**
     * Get count of earned badges for a user
     */
    public long countEarnedByUserId(UUID userId) {
        return count("user.id = ?1 and earned = true", userId);
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