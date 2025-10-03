package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Shared calculator for single trip distance badges with different thresholds.
 * Used by MarathonRunner, CenturyRider, LongDistance, etc.
 */
@ApplicationScoped
public class SingleTripDistanceBadgeCalculator {

    private static final String MAX_DISTANCE_QUERY = """
            SELECT distance_meters, timestamp
            FROM timeline_trips
            WHERE user_id = :userId
            ORDER BY distance_meters DESC
            LIMIT 1
            """;

    private final EntityManager entityManager;

    public SingleTripDistanceBadgeCalculator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Badge calculateSingleTripDistanceBadge(UUID userId, String badgeId, String title, String icon,
                                                   int thresholdMeters, String description) {
        Query query = entityManager.createNativeQuery(MAX_DISTANCE_QUERY);
        query.setParameter("userId", userId);

        List<Object[]> result = (List<Object[]>) query.getResultList();
        if (result == null || result.isEmpty()) {
            return Badge.builder()
                    .id(badgeId)
                    .icon(icon)
                    .title(title)
                    .description(description)
                    .target(thresholdMeters)
                    .current(0)
                    .progress(0)
                    .earned(false)
                    .build();
        }

        int maxDistance = ((Number) result.get(0)[0]).intValue();
        int maxDistanceKm = maxDistance / 1000;
        LocalDateTime maxDate = ((Timestamp) result.get(0)[1]).toLocalDateTime();

        int thresholdKm = thresholdMeters / 1000;

        return Badge.builder()
                .id(badgeId)
                .icon(icon)
                .title(title)
                .description(description)
                .target(thresholdKm)
                .current(maxDistanceKm)
                .progress(maxDistanceKm >= thresholdKm ? 100 : (maxDistanceKm * 100) / thresholdKm)
                .earned(maxDistanceKm >= thresholdKm)
                .earnedDate(maxDistanceKm >= thresholdKm ? maxDate.format(DateTimeFormatter.ISO_DATE) : null)
                .build();
    }
}
