package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class ContinentalCruiserBadgeCalculator implements BadgeCalculator {

    private static final int TOTAL_DISTANCE_THRESHOLD_KM = 10000; // 10,000 km
    private static final String TITLE = "Continental Cruiser";

    private final TotalDistanceBadgeCalculator totalDistanceBadgeCalculator;

    public ContinentalCruiserBadgeCalculator(TotalDistanceBadgeCalculator totalDistanceBadgeCalculator) {
        this.totalDistanceBadgeCalculator = totalDistanceBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "continental_cruiser";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        return totalDistanceBadgeCalculator.calculateTotalDistanceBadge(
                userId, "continental_cruiser", TITLE, "🌏", TOTAL_DISTANCE_THRESHOLD_KM, "Travel 10,000+ km total");
    }
}
