package org.github.tess1o.geopulse.digest.service.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.time.Instant;
import java.util.UUID;

/**
 * Repository for digest-related database queries.
 * Isolates all database access from business logic.
 */
@ApplicationScoped
public class DigestDataRepository {

    @Inject
    EntityManager entityManager;

    /**
     * Get count of unique cities visited during the period.
     * Cities are extracted from the city field in favorite_locations or reverse_geocoding_location tables.
     *
     * @param userId User ID
     * @param start  Period start time
     * @param end    Period end time
     * @return Count of unique cities
     */
    public int getUniqueCitiesCount(UUID userId, Instant start, Instant end) {
        String sql = """
                SELECT COUNT(DISTINCT city_name)
                FROM (
                    SELECT COALESCE(f.city, r.city) as city_name
                    FROM timeline_stays ts
                    LEFT JOIN favorite_locations f ON ts.favorite_id = f.id
                    LEFT JOIN reverse_geocoding_location r ON ts.geocoding_id = r.id
                    WHERE ts.user_id = :userId
                    AND ts.timestamp >= :start
                    AND ts.timestamp <= :end
                    AND (f.city IS NOT NULL OR r.city IS NOT NULL)
                ) AS cities
                """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("userId", userId);
        query.setParameter("start", start);
        query.setParameter("end", end);

        Number result = (Number) query.getSingleResult();
        return result != null ? result.intValue() : 0;
    }

    /**
     * Get count of unique places (location names) visited during the period.
     * This counts distinct location_name values which represent specific named locations.
     *
     * @param userId User ID
     * @param start  Period start time
     * @param end    Period end time
     * @return Count of unique places
     */
    public int getUniquePlacesCount(UUID userId, Instant start, Instant end) {
        String sql = """
                SELECT COUNT(DISTINCT location_name)
                FROM (
                    SELECT COALESCE(f.name, r.display_name) as location_name
                    FROM timeline_stays ts
                    LEFT JOIN favorite_locations f ON ts.favorite_id = f.id
                    LEFT JOIN reverse_geocoding_location r ON ts.geocoding_id = r.id
                    WHERE ts.user_id = :userId
                    AND ts.timestamp >= :start
                    AND ts.timestamp <= :end
                    AND (f.name IS NOT NULL OR r.display_name IS NOT NULL)
                ) AS places
                """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("userId", userId);
        query.setParameter("start", start);
        query.setParameter("end", end);

        Number result = (Number) query.getSingleResult();
        return result != null ? result.intValue() : 0;
    }
}
