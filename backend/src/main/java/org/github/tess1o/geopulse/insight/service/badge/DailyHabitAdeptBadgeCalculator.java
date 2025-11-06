package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class DailyHabitAdeptBadgeCalculator implements BadgeCalculator {

    private static final int CONSECUTIVE_DAYS_THRESHOLD = 30;
    private static final String TITLE = "Daily Habit Adept";

    private final ConsecutiveTripDaysBadgeCalculator consecutiveTripDaysBadgeCalculator;

    public DailyHabitAdeptBadgeCalculator(ConsecutiveTripDaysBadgeCalculator consecutiveTripDaysBadgeCalculator) {
        this.consecutiveTripDaysBadgeCalculator = consecutiveTripDaysBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "daily_habit_30";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        return consecutiveTripDaysBadgeCalculator.calculateConsecutiveTripDaysBadge(
                userId, getBadgeId(), TITLE, "📆", CONSECUTIVE_DAYS_THRESHOLD, "Travel every day for 30 consecutive days");
    }
}
