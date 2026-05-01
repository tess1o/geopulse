package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class TenthFlightBadgeCalculator implements BadgeCalculator {

    private static final int TRIPS_THRESHOLD = 10;
    private static final String TITLE = "10 Flight Trips";

    private final MovementTypeTripCountBadgeCalculator movementTypeTripCountBadgeCalculator;

    public TenthFlightBadgeCalculator(MovementTypeTripCountBadgeCalculator movementTypeTripCountBadgeCalculator) {
        this.movementTypeTripCountBadgeCalculator = movementTypeTripCountBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "flight_trips_10";
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
                "Complete 10 flight trips"
        );
    }
}
