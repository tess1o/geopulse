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

@ApplicationScoped
public class LongDistanceBadgeCalculator implements BadgeCalculator {

    private static final int TARGET_DISTANCE = 500;
    private static final String LONG_DISTANCE_TRIP_QUERY = """
                SELECT distance_km, timestamp
                FROM timeline_trips
                where user_id = :userId
                ORDER BY distance_km DESC
                LIMIT 1;
            """;

    private EntityManager entityManager;

    public LongDistanceBadgeCalculator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        Query distanceQuery = entityManager.createNativeQuery(LONG_DISTANCE_TRIP_QUERY);
        distanceQuery.setParameter("userId", userId);

        List<Object[]> result = (List<Object[]>) distanceQuery.getResultList();
        int maxDistance = result != null ? ((Double) result.get(0)[0]).intValue() : 0;
        LocalDateTime maxDate = result != null ? ((Timestamp) result.get(0)[1]).toLocalDateTime() : null;
        return Badge.builder()
                .id("long_distance")
                .icon("\uD83D\uDEE3\uFE0F")
                .title("Long Distance")
                .description("Travelled %dkm in a single trip".formatted(TARGET_DISTANCE))
                .target(TARGET_DISTANCE)
                .current(maxDistance)
                .progress(maxDistance >= TARGET_DISTANCE ? 100 : (maxDistance * 100) / TARGET_DISTANCE)
                .earned(maxDistance >= TARGET_DISTANCE)
                .earnedDate(maxDistance >= TARGET_DISTANCE ? maxDate.format(DateTimeFormatter.ISO_DATE) : null)
                .build();
    }
}
