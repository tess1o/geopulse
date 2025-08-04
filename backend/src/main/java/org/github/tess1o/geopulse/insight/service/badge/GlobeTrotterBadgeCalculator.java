package org.github.tess1o.geopulse.insight.service.badge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.Badge;

import java.util.UUID;

//🌍 Globe Trotter - Visited 10+ cities
@ApplicationScoped
public class GlobeTrotterBadgeCalculator implements BadgeCalculator {
    private static final int CITIES_THRESHOLD = 10;

    private static final String QUERY = """
            SELECT
                count(distinct COALESCE(f.city, r.city))
            FROM timeline_stays ts
                     LEFT JOIN favorite_locations f ON ts.favorite_id = f.id
                     LEFT JOIN reverse_geocoding_location r ON ts.geocoding_id = r.id
            WHERE ts.user_id = :userId AND
              (f.city IS NOT NULL OR r.city IS NOT NULL)
            """;

    private final EntityManager entityManager;

    public GlobeTrotterBadgeCalculator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Badge calculateBadge(UUID userId) {
        Query citiesQuery = entityManager.createNativeQuery(QUERY);
        citiesQuery.setParameter("userId", userId);

        Number result = (Number) citiesQuery.getSingleResult();
        int totalCitiesVisited = result != null ? result.intValue() : 0;

        return Badge.builder()
                .id("globe_trotter")
                .title("Globe Trotter")
                .icon("\uD83C\uDF0D")
                .description("Visited 10+ cities")
                .progress(totalCitiesVisited >= CITIES_THRESHOLD ? 100 : (totalCitiesVisited * 100) / CITIES_THRESHOLD)
                .earned(totalCitiesVisited >= CITIES_THRESHOLD)
                .current(totalCitiesVisited)
                .target(CITIES_THRESHOLD)
                .build();
    }
}
