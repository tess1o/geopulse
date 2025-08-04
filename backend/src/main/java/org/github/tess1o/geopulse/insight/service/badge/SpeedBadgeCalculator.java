package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class SpeedBadgeCalculator {

    private static final int MAX_JUMP = 2000;
    private static final int MAX_SPEED_KM_H = 210;

    private static final String QUERY = """
            WITH ordered AS (
                SELECT
                    *,
                    LAG(coordinates) OVER (PARTITION BY user_id ORDER BY timestamp) AS prev_coords,
                    LAG(timestamp) OVER (PARTITION BY user_id ORDER BY timestamp) AS prev_ts
                FROM gps_points
                WHERE timestamp IS NOT NULL AND coordinates IS NOT NULL
                    and user_id = :userId
            ),
                 with_speeds AS (
                     SELECT *,
                            EXTRACT(EPOCH FROM (timestamp - prev_ts)) AS seconds_diff,
                            ST_DistanceSphere(coordinates, prev_coords) AS meters_diff
                     FROM ordered
                     WHERE prev_coords IS NOT NULL AND timestamp > prev_ts
                 ),
                 filtered AS (
                     SELECT *,
                            (meters_diff / seconds_diff) * 3.6 AS computed_speed_kmh
                     FROM with_speeds
                     WHERE
                         seconds_diff BETWEEN 3 AND 120  -- 3s to 5min: adjust as needed
                       AND meters_diff < :maxJump          -- ignore huge jumps (> 2km)
                 )
            SELECT MAX(computed_speed_kmh)
            FROM filtered
            WHERE computed_speed_kmh < :maxSpeedKmH
            """;

    private final EntityManager entityManager;

    public SpeedBadgeCalculator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Badge calculateSpeedBadget(UUID userId, String title, int targetSpeedKmH) {
        var query = entityManager.createNativeQuery(QUERY);
        query.setParameter("userId", userId);
        query.setParameter("maxSpeedKmH", MAX_SPEED_KM_H);
        query.setParameter("maxJump", MAX_JUMP);

        Number result = (Number) query.getSingleResult();
        int maxSpeed = result != null ? result.intValue() : 0;
        return Badge.builder()
                .id("speed_deamon_%d".formatted(targetSpeedKmH))
                .icon("\uD83C\uDFC3")
                .title(title)
                .description("Max speed of %d km/h".formatted(targetSpeedKmH))
                .current(maxSpeed)
                .target(targetSpeedKmH)
                .progress(maxSpeed >= targetSpeedKmH ? 100 : (maxSpeed * 100) / targetSpeedKmH)
                .earned(maxSpeed >= targetSpeedKmH)
                .build();
    }
}
