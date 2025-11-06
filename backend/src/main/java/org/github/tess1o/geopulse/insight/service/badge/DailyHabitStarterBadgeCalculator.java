package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class DailyHabitStarterBadgeCalculator implements BadgeCalculator {

    private static final int CONSECUTIVE_DAYS_THRESHOLD = 10;
    private static final String TITLE = "Daily Habit Starter";

    private final ConsecutiveTripDaysBadgeCalculator consecutiveTripDaysBadgeCalculator;

    public DailyHabitStarterBadgeCalculator(ConsecutiveTripDaysBadgeCalculator consecutiveTripDaysBadgeCalculator) {
        this.consecutiveTripDaysBadgeCalculator = consecutiveTripDaysBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "daily_habit_10";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        return consecutiveTripDaysBadgeCalculator.calculateConsecutiveTripDaysBadge(
                userId, getBadgeId(), TITLE, "📅", CONSECUTIVE_DAYS_THRESHOLD, "Travel every day for 10 consecutive days");
    }
}
