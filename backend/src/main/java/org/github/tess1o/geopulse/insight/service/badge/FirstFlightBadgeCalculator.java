package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class FirstFlightBadgeCalculator implements BadgeCalculator {

    private static final int TRIPS_THRESHOLD = 1;
    private static final String TITLE = "First Flight";

    private final MovementTypeTripCountBadgeCalculator movementTypeTripCountBadgeCalculator;

    public FirstFlightBadgeCalculator(MovementTypeTripCountBadgeCalculator movementTypeTripCountBadgeCalculator) {
        this.movementTypeTripCountBadgeCalculator = movementTypeTripCountBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "flight_trips_1";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        return movementTypeTripCountBadgeCalculator.calculateMovementTypeTripCountBadge(
                userId,
                getBadgeId(),
                TITLE,
                "✈️",
                "FLIGHT",
                TRIPS_THRESHOLD,
                "Complete your first flight trip"
        );
    }
}
