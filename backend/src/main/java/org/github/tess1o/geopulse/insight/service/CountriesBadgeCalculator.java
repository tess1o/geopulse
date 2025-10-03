package org.github.tess1o.geopulse.insight.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

@ApplicationScoped
public class CountriesBadgeCalculator {
    private static final String QUERY = """
            SELECT COUNT(DISTINCT COALESCE(f.country, r.country))
            FROM timeline_stays ts
            LEFT JOIN favorite_locations f ON ts.favorite_id = f.id
            LEFT JOIN reverse_geocoding_location r ON ts.geocoding_id = r.id
            WHERE ts.user_id = :userId
            AND (f.country IS NOT NULL OR r.country IS NOT NULL)
            """;

    private final EntityManager entityManager;

    public CountriesBadgeCalculator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Badge calculateCountriesBadge(UUID userId, String badgeId, String title, String icon, int threshold) {
        Query citiesQuery = entityManager.createNativeQuery(QUERY);
        citiesQuery.setParameter("userId", userId);

        Number result = (Number) citiesQuery.getSingleResult();
        int totalCitiesVisited = result != null ? result.intValue() : 0;

        return Badge.builder()
                .id(badgeId)
                .title(title)
                .icon(icon)
                .description("Visit %d+ %s".formatted(threshold, threshold == 1 ? "country" : "countries"))
                .progress(totalCitiesVisited >= threshold ? 100 : (totalCitiesVisited * 100) / threshold)
                .earned(totalCitiesVisited >= threshold)
                .current(totalCitiesVisited)
                .target(threshold)
                .build();
    }
}
