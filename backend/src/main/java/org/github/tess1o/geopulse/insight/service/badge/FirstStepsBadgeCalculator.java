package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.Badge;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;

import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.UUID;

@ApplicationScoped
public class FirstStepsBadgeCalculator implements BadgeCalculator {

    private static final String FIRST_TRIP_QUERY = """
            SELECT timestamp as timestamp_utc
            FROM timeline_trips
            WHERE user_id = :userId
            AND distance_meters >= 1000
            ORDER BY timestamp ASC
            LIMIT 1
            """;

    private final EntityManager entityManager;

    public FirstStepsBadgeCalculator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public String getBadgeId() {
        return "first_steps";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        Query query = entityManager.createNativeQuery(FIRST_TRIP_QUERY);
        query.setParameter("userId", userId);

        Object result = query.getResultList().stream().findFirst().orElse(null);
        boolean earned = result != null;
        String earnedDate = null;
        if (earned) {
            var earnedInstant = TimestampUtils.getInstantSafe(result);
            if (earnedInstant != null) {
                earnedDate = earnedInstant.atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ISO_DATE);
            }
        }

        return Badge.builder()
                .id(getBadgeId())
                .icon("👣")
                .title("First Steps")
                .description("Complete your first trip (≥1 km)")
                .earned(earned)
                .earnedDate(earnedDate)
                .build();
    }
}
