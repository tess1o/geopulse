package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class FirstTrainBadgeCalculator implements BadgeCalculator {

    private static final int TRIPS_THRESHOLD = 1;
    private static final String TITLE = "First Train";

    private final MovementTypeTripCountBadgeCalculator movementTypeTripCountBadgeCalculator;

    public FirstTrainBadgeCalculator(MovementTypeTripCountBadgeCalculator movementTypeTripCountBadgeCalculator) {
        this.movementTypeTripCountBadgeCalculator = movementTypeTripCountBadgeCalculator;
    }

    @Override
    public String getBadgeId() {
        return "train_trips_1";
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
                "Complete your first train trip"
        );
    }
}
