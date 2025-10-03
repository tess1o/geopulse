package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class MarathonRunnerBadgeCalculator implements BadgeCalculator {

    private static final int TARGET_DISTANCE = 42195; // 42.195 km in meters (marathon distance)
    private static final String TITLE = "Marathon Runner";

    private final SingleTripDistanceBadgeCalculator singleTripDistanceBadgeCalculator;

    public MarathonRunnerBadgeCalculator(SingleTripDistanceBadgeCalculator singleTripDistanceBadgeCalculator) {
        this.singleTripDistanceBadgeCalculator = singleTripDistanceBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "marathon_runner";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        return singleTripDistanceBadgeCalculator.calculateSingleTripDistanceBadge(
                userId, "marathon_runner", TITLE, "🏃‍♂️", TARGET_DISTANCE, "Complete a trip of 42+ km");
    }
}
