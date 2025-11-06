package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class LongDistanceBadgeCalculator implements BadgeCalculator {

    private static final int TARGET_DISTANCE_METERS = 500000; // 500 km in meters
    private static final String TITLE = "Long Distance";

    private final SingleTripDistanceBadgeCalculator singleTripDistanceBadgeCalculator;

    public LongDistanceBadgeCalculator(SingleTripDistanceBadgeCalculator singleTripDistanceBadgeCalculator) {
        this.singleTripDistanceBadgeCalculator = singleTripDistanceBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "target_trip_distance_500";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        return singleTripDistanceBadgeCalculator.calculateSingleTripDistanceBadge(
                userId, getBadgeId(), TITLE, "🛣️", TARGET_DISTANCE_METERS, "Travelled 500km in a single trip");
    }
}
