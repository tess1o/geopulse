package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class TranscontinentalTravelerBadgeCalculator implements BadgeCalculator {

    private static final int TOTAL_DISTANCE_THRESHOLD_KM = 50_000; // 50,000 km
    private static final String TITLE = "Transcontinental Traveler";

    private final TotalDistanceBadgeCalculator totalDistanceBadgeCalculator;

    public TranscontinentalTravelerBadgeCalculator(TotalDistanceBadgeCalculator totalDistanceBadgeCalculator) {
        this.totalDistanceBadgeCalculator = totalDistanceBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "total_distance_50000";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        return totalDistanceBadgeCalculator.calculateTotalDistanceBadge(
                userId, getBadgeId(), TITLE, "🌎", TOTAL_DISTANCE_THRESHOLD_KM, "Travel 50,000+ km total");
    }
}
