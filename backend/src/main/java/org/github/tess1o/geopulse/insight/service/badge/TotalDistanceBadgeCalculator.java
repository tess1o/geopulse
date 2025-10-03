package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

/**
 * Shared calculator for cumulative distance badges with different thresholds.
 * Used by RoadWarrior, ContinentalCruiser, etc.
 */
@ApplicationScoped
public class TotalDistanceBadgeCalculator {

    private static final String TOTAL_DISTANCE_QUERY = """
            SELECT SUM(distance_meters)
            FROM timeline_trips
            WHERE user_id = :userId
            """;

    private final EntityManager entityManager;

    public TotalDistanceBadgeCalculator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Badge calculateTotalDistanceBadge(UUID userId, String badgeId, String title, String icon,
                                              int thresholdKm, String description) {
        Query distanceQuery = entityManager.createNativeQuery(TOTAL_DISTANCE_QUERY);
        distanceQuery.setParameter("userId", userId);

        Number result = (Number) distanceQuery.getSingleResult();
        int totalDistanceMeters = result != null ? result.intValue() : 0;
        int totalDistanceKm = totalDistanceMeters / 1000;

        return Badge.builder()
                .id(badgeId)
                .current(totalDistanceKm)
                .icon(icon)
                .title(title)
                .description(description)
                .progress(totalDistanceKm >= thresholdKm ? 100 : (totalDistanceKm * 100) / thresholdKm)
                .target(thresholdKm)
                .earned(totalDistanceKm >= thresholdKm)
                .build();
    }
}
