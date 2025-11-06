package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class GlobalNomadBadgeCalculator implements BadgeCalculator {

    private static final int TOTAL_DISTANCE_THRESHOLD_KM = 100_000; // 100,000 km
    private static final String TITLE = "Global Nomad";

    private final TotalDistanceBadgeCalculator totalDistanceBadgeCalculator;

    public GlobalNomadBadgeCalculator(TotalDistanceBadgeCalculator totalDistanceBadgeCalculator) {
        this.totalDistanceBadgeCalculator = totalDistanceBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "total_distance_100000";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        return totalDistanceBadgeCalculator.calculateTotalDistanceBadge(
                userId, getBadgeId(), TITLE, "✈️", TOTAL_DISTANCE_THRESHOLD_KM, "Travel 100,000+ km total");
    }
}
