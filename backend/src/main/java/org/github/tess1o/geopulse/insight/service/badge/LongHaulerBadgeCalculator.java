package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.Badge;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;

import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class LongHaulerBadgeCalculator implements BadgeCalculator {

    private static final int TARGET_DURATION_MINUTES = 240; // 4 hours in minutes
    private static final int TARGET_DURATION_SECONDS = 14400; // 4 hours in seconds
    private static final String LONG_DURATION_QUERY = """
            SELECT trip_duration, timestamp as timestamp_utc
            FROM timeline_trips
            WHERE user_id = :userId
            ORDER BY trip_duration DESC
            LIMIT 1
            """;

    private final EntityManager entityManager;

    public LongHaulerBadgeCalculator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public String getBadgeId() {
        return "long_hauler";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        Query query = entityManager.createNativeQuery(LONG_DURATION_QUERY);
        query.setParameter("userId", userId);

        List<Object[]> result = (List<Object[]>) query.getResultList();
        if (result == null || result.isEmpty()) {
            return Badge.builder()
                    .id(getBadgeId())
                    .icon("🚛")
                    .title("Long Hauler")
                    .description("Complete a trip lasting 4+ hours")
                    .target(TARGET_DURATION_MINUTES)
                    .current(0)
                    .progress(0)
                    .earned(false)
                    .build();
        }

        int maxDurationSeconds = ((Number) result.get(0)[0]).intValue();
        int maxDurationMinutes = maxDurationSeconds / 60;
        String earnedDate = null;
        if (maxDurationSeconds >= TARGET_DURATION_SECONDS) {
            var maxDateInstant = TimestampUtils.getInstantSafe(result.get(0)[1]);
            if (maxDateInstant != null) {
                earnedDate = maxDateInstant.atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ISO_DATE);
            }
        }

        return Badge.builder()
                .id(getBadgeId())
                .icon("🚛")
                .title("Long Hauler")
                .description("Complete a trip lasting 4+ hours")
                .target(TARGET_DURATION_MINUTES)
                .current(maxDurationMinutes)
                .progress(maxDurationSeconds >= TARGET_DURATION_SECONDS ? 100 : (maxDurationSeconds * 100) / TARGET_DURATION_SECONDS)
                .earned(maxDurationSeconds >= TARGET_DURATION_SECONDS)
                .earnedDate(earnedDate)
                .build();
    }
}
