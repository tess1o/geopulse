package org.github.tess1o.geopulse.insight.service;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.insight.model.Achievements;
import org.github.tess1o.geopulse.insight.model.Badge;
import org.github.tess1o.geopulse.insight.model.UserBadgeEntity;
import org.github.tess1o.geopulse.insight.repository.UserBadgeRepository;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class AchievementService {

    private final UserBadgeRepository userBadgeRepository;
    private final BadgeRecalculationService badgeRecalculationService;

    public AchievementService(UserBadgeRepository userBadgeRepository,
                            BadgeRecalculationService badgeRecalculationService) {
        this.userBadgeRepository = userBadgeRepository;
        this.badgeRecalculationService = badgeRecalculationService;
    }

    /**
     * Get achievements for a user from persistent badge storage.
     * If no badges exist for the user, initializes them first.
     */
    public Achievements calculateAchievements(UUID userId) {
        List<Badge> badges = getBadgesFromDatabase(userId);
        return new Achievements(badges);
    }

    /**
     * Get badges from database. If no badges exist, initialize them for the user.
     */
    private List<Badge> getBadgesFromDatabase(UUID userId) {
        // Check if user has any badges stored
        if (!userBadgeRepository.existsByUserId(userId)) {
            log.info("No badges found for user {}, initializing badges", userId);
            badgeRecalculationService.initializeBadgesForUser(userId);
        }

        // Get all badges from database and convert to Badge model
        List<UserBadgeEntity> userBadges = userBadgeRepository.findByUserId(userId);
        
        return userBadges.stream()
                .map(UserBadgeEntity::toBadge)
                .sorted(
                        Comparator.comparing(Badge::isEarned)
                                .thenComparing(Badge::getId)
                )
                .toList();
    }
}