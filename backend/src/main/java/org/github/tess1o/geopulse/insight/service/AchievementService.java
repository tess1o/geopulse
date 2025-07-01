package org.github.tess1o.geopulse.insight.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import org.github.tess1o.geopulse.insight.model.Achievements;
import org.github.tess1o.geopulse.insight.model.Badge;
import org.github.tess1o.geopulse.insight.service.badge.BadgeCalculator;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@ApplicationScoped
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
                .map(calculator -> calculator.calculateBadge(userId))
                .collect(Collectors.toList());
    }
}