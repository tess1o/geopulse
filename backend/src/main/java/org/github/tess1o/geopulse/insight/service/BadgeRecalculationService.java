package org.github.tess1o.geopulse.insight.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.insight.model.Badge;
import org.github.tess1o.geopulse.insight.model.UserBadgeEntity;
import org.github.tess1o.geopulse.insight.repository.UserBadgeRepository;
import org.github.tess1o.geopulse.insight.service.badge.BadgeCalculator;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

@ApplicationScoped
@Slf4j
public class BadgeRecalculationService {

    private final Instance<BadgeCalculator> badgeCalculators;
    private final UserBadgeRepository userBadgeRepository;

    public BadgeRecalculationService(Instance<BadgeCalculator> badgeCalculators,
                                     UserBadgeRepository userBadgeRepository) {
        this.badgeCalculators = badgeCalculators;
        this.userBadgeRepository = userBadgeRepository;
    }

    /**
     * Recalculate ALL badges for a specific user.
     * Used after import completion or timeline full regeneration.
     * This method completely replaces all existing badges for the user.
     */
    @Transactional
    public void recalculateAllBadgesForUser(UUID userId) {
        log.info("Starting full badge recalculation for user: {}", userId);

        try {
            // Find the user entity
            UserEntity user = UserEntity.findById(userId);
            if (user == null) {
                log.warn("User {} not found, skipping badge recalculation", userId);
                return;
            }

            // Delete existing badges for this user
            userBadgeRepository.deleteByUserId(userId);
            log.debug("Deleted existing badges for user: {}", userId);

            // Calculate and store all badges
            List<UserBadgeEntity> newBadges = calculateAllBadgesForUser(user);

            // Persist all badges
            for (UserBadgeEntity badge : newBadges) {
                userBadgeRepository.persist(badge);
            }

            log.info("Successfully recalculated {} badges for user: {}", newBadges.size(), userId);

        } catch (Exception e) {
            log.error("Failed to recalculate badges for user {}: {}", userId, e.getMessage(), e);
            throw e; // Re-throw to trigger transaction rollback
        }
    }

    /**
     * Calculate all badges for a user and return them as entities
     */
    private List<UserBadgeEntity> calculateAllBadgesForUser(UserEntity user) {
        return StreamSupport.stream(badgeCalculators.spliterator(), false)
                .map(calculator -> calculateUserBadgeEntity(user, calculator))
                .filter(badge -> badge != null)
                .toList();
    }

    /**
     * Calculate a single badge for a user and convert to entity
     */
    private UserBadgeEntity calculateUserBadgeEntity(UserEntity user, BadgeCalculator calculator) {
        try {
            Badge badge = calculator.calculateBadge(user.getId());
            if (badge == null) {
                return null;
            }
            return UserBadgeEntity.fromBadge(badge, user.getId());
        } catch (Exception e) {
            log.error("Error calculating badge {} for user {} with calculator {}: {}",
                    calculator.getBadgeId(), user.getId(), calculator.getClass().getSimpleName(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Initialize badges for a new user (populate all badges with 0 progress)
     */
    @Transactional
    public void initializeBadgesForUser(UUID userId) {
        log.info("Initializing badges for new user: {}", userId);

        if (userBadgeRepository.existsByUserId(userId)) {
            log.debug("User {} already has badges, skipping initialization", userId);
            return;
        }

        recalculateAllBadgesForUser(userId);
    }

}