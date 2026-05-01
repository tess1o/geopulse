package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class FifthTrainBadgeCalculator implements BadgeCalculator {

    private static final int TRIPS_THRESHOLD = 5;
    private static final String TITLE = "5 Train Trips";

    private final MovementTypeTripCountBadgeCalculator movementTypeTripCountBadgeCalculator;

    public FifthTrainBadgeCalculator(MovementTypeTripCountBadgeCalculator movementTypeTripCountBadgeCalculator) {
        this.movementTypeTripCountBadgeCalculator = movementTypeTripCountBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "train_trips_5";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        return movementTypeTripCountBadgeCalculator.calculateMovementTypeTripCountBadge(
                userId,
                getBadgeId(),
                TITLE,
                "🚆",
                "TRAIN",
                TRIPS_THRESHOLD,
                "Complete 5 train trips"
        );
    }
}
