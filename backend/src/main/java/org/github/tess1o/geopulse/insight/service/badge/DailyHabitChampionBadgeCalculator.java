package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class DailyHabitChampionBadgeCalculator implements BadgeCalculator {

    private static final int CONSECUTIVE_DAYS_THRESHOLD = 120;
    private static final String TITLE = "Daily Habit Champion";

    private final ConsecutiveTripDaysBadgeCalculator consecutiveTripDaysBadgeCalculator;

    public DailyHabitChampionBadgeCalculator(ConsecutiveTripDaysBadgeCalculator consecutiveTripDaysBadgeCalculator) {
        this.consecutiveTripDaysBadgeCalculator = consecutiveTripDaysBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "daily_habit_120";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        return consecutiveTripDaysBadgeCalculator.calculateConsecutiveTripDaysBadge(
                userId, getBadgeId(), TITLE, "🏆", CONSECUTIVE_DAYS_THRESHOLD, "Travel every day for 120 consecutive days");
    }
}
