package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

/**
 * Shared calculator for city-based badges with different thresholds.
 * Used by CityStarter, GlobeTrotter, etc.
 */
@ApplicationScoped
public class CitiesBadgeCalculator {

    private static final String CITIES_COUNT_QUERY = """
            SELECT COUNT(DISTINCT COALESCE(f.city, r.city))
            FROM timeline_stays ts
            LEFT JOIN favorite_locations f ON ts.favorite_id = f.id
            LEFT JOIN reverse_geocoding_location r ON ts.geocoding_id = r.id
            WHERE ts.user_id = :userId
            AND (f.city IS NOT NULL OR r.city IS NOT NULL)
            """;

    private final EntityManager entityManager;

    public CitiesBadgeCalculator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Badge calculateCitiesBadge(UUID userId, String badgeId, String title, String icon, int threshold) {
        Query citiesQuery = entityManager.createNativeQuery(CITIES_COUNT_QUERY);
        citiesQuery.setParameter("userId", userId);

        Number result = (Number) citiesQuery.getSingleResult();
        int totalCitiesVisited = result != null ? result.intValue() : 0;

        return Badge.builder()
                .id(badgeId)
                .title(title)
                .icon(icon)
                .description("Visit %d+ %s".formatted(threshold, threshold == 1 ? "city" : "cities"))
                .progress(totalCitiesVisited >= threshold ? 100 : (totalCitiesVisited * 100) / threshold)
                .earned(totalCitiesVisited >= threshold)
                .current(totalCitiesVisited)
                .target(threshold)
                .build();
    }
}
