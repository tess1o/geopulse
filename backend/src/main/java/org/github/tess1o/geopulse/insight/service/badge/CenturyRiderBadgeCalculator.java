package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class CenturyRiderBadgeCalculator implements BadgeCalculator {

    private static final int TARGET_DISTANCE = 100000; // 100 km in meters
    private static final String TITLE = "Century Rider";

    private final SingleTripDistanceBadgeCalculator singleTripDistanceBadgeCalculator;

    public CenturyRiderBadgeCalculator(SingleTripDistanceBadgeCalculator singleTripDistanceBadgeCalculator) {
        this.singleTripDistanceBadgeCalculator = singleTripDistanceBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "century_rider";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        return singleTripDistanceBadgeCalculator.calculateSingleTripDistanceBadge(
                userId, "century_rider", TITLE, "💯", TARGET_DISTANCE, "Complete a trip of 100+ km");
    }
}
