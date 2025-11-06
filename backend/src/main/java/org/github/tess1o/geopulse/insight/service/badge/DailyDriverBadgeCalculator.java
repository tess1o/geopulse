package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class DailyDriverBadgeCalculator implements BadgeCalculator {

    private static final int TARGET_DISTANCE = 50000; // 50 km in meters
    private static final String DAILY_DISTANCE_QUERY = """
            SELECT MAX(daily_distance) as max_daily_distance
            FROM (
                SELECT
                    DATE(t.timestamp AT TIME ZONE u.timezone) as trip_date,
                    SUM(t.distance_meters) as daily_distance
                FROM timeline_trips t
                JOIN users u ON t.user_id = u.id
                WHERE t.user_id = :userId
                GROUP BY trip_date
            ) daily_totals
            """;

    private final EntityManager entityManager;

    public DailyDriverBadgeCalculator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public String getBadgeId() {
        return "daily_driver";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        Query query = entityManager.createNativeQuery(DAILY_DISTANCE_QUERY);
        query.setParameter("userId", userId);

        Number result = (Number) query.getSingleResult();
        int maxDailyDistance = result != null ? result.intValue() : 0;

        return Badge.builder()
                .id(getBadgeId())
                .icon("🚙")
                .title("Daily Driver")
                .description("Travel 50+ km in a single day")
                .target(TARGET_DISTANCE)
                .current(maxDailyDistance)
                .progress(maxDailyDistance >= TARGET_DISTANCE ? 100 : (maxDailyDistance * 100) / TARGET_DISTANCE)
                .earned(maxDailyDistance >= TARGET_DISTANCE)
                .build();
    }
}
