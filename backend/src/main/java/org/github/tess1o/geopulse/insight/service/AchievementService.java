package org.github.tess1o.geopulse.insight.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.insight.model.Achievements;
import org.github.tess1o.geopulse.insight.model.Badge;
import org.github.tess1o.geopulse.insight.service.badge.BadgeCalculator;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@ApplicationScoped
@Slf4j
public class AchievementService {

    private final Instance<BadgeCalculator> badgeCalculators;

    public AchievementService(Instance<BadgeCalculator> badgeCalculators) {
        this.badgeCalculators = badgeCalculators;
    }

    public Achievements calculateAchievements(UUID userId) {
        List<Badge> badges = calculateBadges(userId);
        return new Achievements(badges);
    }

    private List<Badge> calculateBadges(UUID userId) {
        return StreamSupport.stream(badgeCalculators.spliterator(), false)
                .map(calculator -> calculateUserBadge(userId, calculator))
                .filter(badge -> badge != null)
                .sorted(
                        Comparator.comparing(Badge::isEarned)
                                .thenComparing(Badge::getId)
                )
                .collect(Collectors.toList());
    }

    private static Badge calculateUserBadge(UUID userId, BadgeCalculator calculator) {
        try {
            return calculator.calculateBadge(userId);
        } catch (Exception e) {
            log.error("Error calculating badge for user " + userId, e);
            return null;
        }
    }

}