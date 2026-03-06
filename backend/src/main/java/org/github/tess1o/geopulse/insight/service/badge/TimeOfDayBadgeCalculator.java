package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.Badge;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;

import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Shared calculator for time-of-day based badges (timezone-aware).
 * Used by EarlyBird, NightOwl, etc.
 */
@ApplicationScoped
public class TimeOfDayBadgeCalculator {

    private static final String TIME_OF_DAY_QUERY = """
            SELECT t.timestamp AT TIME ZONE 'UTC' as timestamp_utc
            FROM timeline_trips t
            JOIN users u ON t.user_id = u.id
            WHERE t.user_id = :userId
            AND %s
            ORDER BY t.timestamp ASC
            LIMIT 1
            """;

    private final EntityManager entityManager;

    public TimeOfDayBadgeCalculator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Calculate a time-of-day badge based on hour conditions.
     *
     * @param userId The user ID
     * @param badgeId Badge identifier
     * @param title Badge title
     * @param icon Badge icon emoji
     * @param hourCondition SQL condition for hour extraction (e.g., "< 6" or ">= 22")
     * @param description Badge description
     * @return Calculated badge
     */
    public Badge calculateTimeOfDayBadge(UUID userId, String badgeId, String title, String icon,
                                          String hourCondition, String description) {
        String hourFilter = "EXTRACT(HOUR FROM t.timestamp AT TIME ZONE u.timezone) " + hourCondition;
        String query = TIME_OF_DAY_QUERY.formatted(hourFilter);

        Query timeQuery = entityManager.createNativeQuery(query);
        timeQuery.setParameter("userId", userId);

        Object result = timeQuery.getResultList().stream().findFirst().orElse(null);
        boolean earned = result != null;
        String earnedDate = null;
        if (earned) {
            var earnedInstant = TimestampUtils.getInstantSafe(result);
            if (earnedInstant != null) {
                earnedDate = earnedInstant.atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ISO_DATE);
            }
        }

        return Badge.builder()
                .id(badgeId)
                .icon(icon)
                .title(title)
                .description(description)
                .earned(earned)
                .earnedDate(earnedDate)
                .build();
    }
}
