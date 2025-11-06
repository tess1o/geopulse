package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class MidnightMoveBadgeCalculator implements BadgeCalculator {

    private static final String TITLE = "Midnight Move";

    private final TimeOfDayBadgeCalculator timeOfDayBadgeCalculator;

    public MidnightMoveBadgeCalculator(TimeOfDayBadgeCalculator timeOfDayBadgeCalculator) {
        this.timeOfDayBadgeCalculator = timeOfDayBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "time_of_day_midnight_move";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        return timeOfDayBadgeCalculator.calculateTimeOfDayBadge(
                userId, getBadgeId(), TITLE, "🌙", ">= 0 AND EXTRACT(HOUR FROM t.timestamp AT TIME ZONE u.timezone) < 5", "Start a trip between midnight and 5:00 AM");
    }
}
