package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class LocalLegendBadgeCalculator implements BadgeCalculator {

    private static final int VISITS_THRESHOLD = 10;
    private static final String QUERY = """
            SELECT MAX(visit_count) as max_visits
            FROM (
                SELECT
                    CASE
                        WHEN ts.favorite_id IS NOT NULL THEN CONCAT('favorite_', ts.favorite_id)
                        WHEN ts.geocoding_id IS NOT NULL THEN CONCAT('geocoding_', ts.geocoding_id)
                        ELSE CONCAT('location_', ST_AsText(ts.location))
                    END as place_id,
                    COUNT(*) as visit_count
                FROM timeline_stays ts
                WHERE ts.user_id = :userId
                GROUP BY place_id
            ) place_visits
            """;

    private final EntityManager entityManager;

    public LocalLegendBadgeCalculator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public String getBadgeId() {
        return "local_legend";
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        Query query = entityManager.createNativeQuery(QUERY);
        query.setParameter("userId", userId);

        Number result = (Number) query.getSingleResult();
        int maxVisits = result != null ? result.intValue() : 0;

        return Badge.builder()
                .id("local_legend")
                .title("Local Legend")
                .icon("🏆")
                .description("Visit the same place 10 times")
                .progress(maxVisits >= VISITS_THRESHOLD ? 100 : (maxVisits * 100) / VISITS_THRESHOLD)
                .earned(maxVisits >= VISITS_THRESHOLD)
                .current(maxVisits)
                .target(VISITS_THRESHOLD)
                .build();
    }
}
