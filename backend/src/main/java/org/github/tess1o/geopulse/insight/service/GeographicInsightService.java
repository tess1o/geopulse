package org.github.tess1o.geopulse.insight.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.insight.model.City;
import org.github.tess1o.geopulse.insight.model.Country;
import org.github.tess1o.geopulse.insight.model.GeographicInsights;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class GeographicInsightService {

    private final EntityManager entityManager;

    public GeographicInsightService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public GeographicInsights calculateGeographicInsights(UUID userId) {
        List<Country> countries = getCountries(userId);
        List<City> cities = getCities(userId);

        return new GeographicInsights(countries, cities);
    }

    private List<Country> getCountries(UUID userId) {
        String sql = """
                SELECT DISTINCT COALESCE(f.country, r.country) as country_name
                FROM timeline_stays ts
                LEFT JOIN favorite_locations f ON ts.favorite_id = f.id
                LEFT JOIN reverse_geocoding_location r ON ts.geocoding_id = r.id
                WHERE ts.user_id = :userId
                AND (f.country IS NOT NULL OR r.country IS NOT NULL)
                """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("userId", userId);

        List<String> countryNames = query.getResultList();
        return countryNames.stream()
                .map(this::mapCountryNameToCountry)
                .collect(java.util.stream.Collectors.toList());
    }

    private Country mapCountryNameToCountry(String countryName) {
        return new Country(countryName);
    }

    private List<City> getCities(UUID userId) {
        String sql = """
                SELECT 
                    COALESCE(f.city, r.city) as city_name,
                    COUNT(*) as visit_count
                FROM timeline_stays ts
                LEFT JOIN favorite_locations f ON ts.favorite_id = f.id
                LEFT JOIN reverse_geocoding_location r ON ts.geocoding_id = r.id
                WHERE ts.user_id = :userId
                AND (f.city IS NOT NULL OR r.city IS NOT NULL)
                GROUP BY city_name
                ORDER BY visit_count DESC
                LIMIT 10
                """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("userId", userId);

        List<Object[]> results = query.getResultList();
        return results.stream()
                .map(row -> new City((String) row[0], ((Number) row[1]).intValue()))
                .collect(java.util.stream.Collectors.toList());
    }
}