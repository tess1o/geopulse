package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class DailyHabitMasterBadgeCalculator implements BadgeCalculator {

    private static final int CONSECUTIVE_DAYS_THRESHOLD = 60;
    private static final String TITLE = "Daily Habit Master";

    private final ConsecutiveTripDaysBadgeCalculator consecutiveTripDaysBadgeCalculator;

    public DailyHabitMasterBadgeCalculator(ConsecutiveTripDaysBadgeCalculator consecutiveTripDaysBadgeCalculator) {
        this.consecutiveTripDaysBadgeCalculator = consecutiveTripDaysBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "daily_habit_60";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        return consecutiveTripDaysBadgeCalculator.calculateConsecutiveTripDaysBadge(
                userId, getBadgeId(), TITLE, "🗓️", CONSECUTIVE_DAYS_THRESHOLD, "Travel every day for 60 consecutive days");
    }
}
