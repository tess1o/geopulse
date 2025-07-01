package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@ApplicationScoped
public class LocalExplorerBadgeCalculator implements BadgeCalculator {

    private final EntityManager entityManager;

    public LocalExplorerBadgeCalculator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        int uniquePlaces = getUniquePlaces(userId);
        int target = 25;
        boolean localExplorerComplete = uniquePlaces >= target;
        int progress = Math.min(100, (uniquePlaces * 100) / target);

        return new Badge(
                "local_explorer",
                "Local Explorer",
                "Discover 25 places in your area",
                "🏠️",
                localExplorerComplete,
                localExplorerComplete ? LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")) : null,
                localExplorerComplete ? null : progress,
                localExplorerComplete ? null : uniquePlaces,
                localExplorerComplete ? null : target
        );
    }

    private int getUniquePlaces(UUID userId) {
        String sql = """
                SELECT COUNT(DISTINCT 
                    CASE 
                        WHEN ts.favorite_id IS NOT NULL THEN CONCAT('favorite_', ts.favorite_id)
                        WHEN ts.geocoding_id IS NOT NULL THEN CONCAT('geocoding_', ts.geocoding_id)
                        ELSE CONCAT('location_', ROUND(CAST(ts.latitude AS numeric), 3), '_', ROUND(CAST(ts.longitude AS numeric), 3))
                    END
                )
                FROM timeline_stays ts
                WHERE ts.user_id = :userId
                """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("userId", userId);

        Number result = (Number) query.getSingleResult();
        return result != null ? result.intValue() : 0;
    }
}