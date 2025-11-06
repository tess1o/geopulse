package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class EarlyBirdBadgeCalculator implements BadgeCalculator {

    private static final String TITLE = "Early Bird";

    private final TimeOfDayBadgeCalculator timeOfDayBadgeCalculator;

    public EarlyBirdBadgeCalculator(TimeOfDayBadgeCalculator timeOfDayBadgeCalculator) {
        this.timeOfDayBadgeCalculator = timeOfDayBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "time_of_day_early_bird";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        return timeOfDayBadgeCalculator.calculateTimeOfDayBadge(
                userId, getBadgeId(), TITLE, "🌅", "< 6", "Start a trip before 6:00 AM");
    }
}
