package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class FifthFlightBadgeCalculator implements BadgeCalculator {

    private static final int TRIPS_THRESHOLD = 5;
    private static final String TITLE = "5 Flight Trips";

    private final MovementTypeTripCountBadgeCalculator movementTypeTripCountBadgeCalculator;

    public FifthFlightBadgeCalculator(MovementTypeTripCountBadgeCalculator movementTypeTripCountBadgeCalculator) {
        this.movementTypeTripCountBadgeCalculator = movementTypeTripCountBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "flight_trips_5";
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
                "Complete 5 flight trips"
        );
    }
}
