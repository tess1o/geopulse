package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.Badge;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Shared calculator for movement-type trip count badges with different thresholds.
 * Used by movement milestones such as first/5th/10th flight/train trips.
 */
@ApplicationScoped
public class MovementTypeTripCountBadgeCalculator {

    private static final String TRIP_COUNT_QUERY = """
            WITH typed_trips AS (
                SELECT
                    t.timestamp as timestamp_utc,
                    ROW_NUMBER() OVER (ORDER BY t.timestamp ASC) AS rn
                FROM timeline_trips t
                WHERE t.user_id = :userId
                  AND t.movement_type = :movementType
            )
            SELECT
                COUNT(*) AS trip_count,
                MIN(CASE WHEN rn = :threshold THEN timestamp_utc END) AS earned_timestamp
            FROM typed_trips
            """;

    private final EntityManager entityManager;

    public MovementTypeTripCountBadgeCalculator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Badge calculateMovementTypeTripCountBadge(UUID userId, String badgeId, String title, String icon,
                                                     String movementType, int threshold, String description) {
        Query query = entityManager.createNativeQuery(TRIP_COUNT_QUERY);
        query.setParameter("userId", userId);
        query.setParameter("movementType", movementType);
        query.setParameter("threshold", threshold);

        Object[] result = (Object[]) query.getSingleResult();
        int totalTrips = result[0] != null ? ((Number) result[0]).intValue() : 0;
        boolean earned = totalTrips >= threshold;

        String earnedDate = null;
        if (earned && result[1] != null) {
            var earnedInstant = TimestampUtils.getInstantSafe(result[1]);
            if (earnedInstant != null) {
                earnedDate = earnedInstant.atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ISO_DATE);
            }
        }

        return Badge.builder()
                .id(badgeId)
                .title(title)
                .icon(icon)
                .description(description)
                .progress(earned ? 100 : (totalTrips * 100) / threshold)
                .earned(earned)
                .current(totalTrips)
                .target(threshold)
                .earnedDate(earnedDate)
                .build();
    }
}
