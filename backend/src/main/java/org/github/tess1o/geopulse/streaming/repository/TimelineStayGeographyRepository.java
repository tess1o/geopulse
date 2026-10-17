package org.github.tess1o.geopulse.streaming.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Timeline stay queries for location analytics: cities and countries a user visited, their statistics, top places,
 * centroids and search, and the aggregated places shown on the map.
 */
@ApplicationScoped
public class TimelineStayGeographyRepository implements PanacheRepository<TimelineStayEntity> {

    /**
     * Get list of all cities with visit counts for a user.
     * Returns city name, country, visit count, total duration, and unique places count.
     *
     * @param userId user ID
     * @return list of Object arrays containing city summary data
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> getCitiesWithCounts(UUID userId) {
        String query = """
                SELECT COALESCE(f.city, g.city) as city,
                       COALESCE(f.country, g.country) as country,
                       COUNT(s) as visitCount,
                       SUM(s.stayDuration) as totalDuration,
                       COUNT(DISTINCT CASE WHEN f.id IS NOT NULL THEN f.id ELSE g.id END) as uniquePlaces
                FROM TimelineStayEntity s
                LEFT JOIN s.favoriteLocation f
                LEFT JOIN s.geocodingLocation g
                WHERE s.user.id = ?1
                  AND COALESCE(f.city, g.city) IS NOT NULL
                GROUP BY COALESCE(f.city, g.city), COALESCE(f.country, g.country)
                ORDER BY visitCount DESC
                """;
        return getEntityManager().createQuery(query)
                .setParameter(1, userId)
                .getResultList();
    }

    /**
     * Get list of all countries with visit counts for a user.
     * Returns country name, visit count, city count, total duration, and unique places count.
     *
     * @param userId user ID
     * @return list of Object arrays containing country summary data
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> getCountriesWithCounts(UUID userId) {
        String query = """
                SELECT COALESCE(f.country, g.country) as country,
                       COUNT(s) as visitCount,
                       COUNT(DISTINCT COALESCE(f.city, g.city)) as cityCount,
                       SUM(s.stayDuration) as totalDuration,
                       COUNT(DISTINCT CASE WHEN f.id IS NOT NULL THEN f.id ELSE g.id END) as uniquePlaces
                FROM TimelineStayEntity s
                LEFT JOIN s.favoriteLocation f
                LEFT JOIN s.geocodingLocation g
                WHERE s.user.id = ?1
                  AND COALESCE(f.country, g.country) IS NOT NULL
                GROUP BY COALESCE(f.country, g.country)
                ORDER BY visitCount DESC
                """;
        return getEntityManager().createQuery(query)
                .setParameter(1, userId)
                .getResultList();
    }

    /**
     * Get aggregated places for map visualization.
     * Results are grouped by place identity and sorted by last visit time descending.
     *
     * @param userId    user ID
     * @param startTime optional start timestamp (inclusive)
     * @param endTime   optional end timestamp (inclusive)
     * @param minLat    optional viewport minimum latitude
     * @param maxLat    optional viewport maximum latitude
     * @param minLon    optional viewport minimum longitude
     * @param maxLon    optional viewport maximum longitude
     * @param minVisits minimum number of visits required
     * @param limit     maximum number of rows to return
     * @return list of Object[] rows:
     * [type, placeId, locationName, visitCount, lastVisit, latitude, longitude, city, country]
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> findMapPlaces(
            UUID userId,
            Instant startTime,
            Instant endTime,
            Double minLat,
            Double maxLat,
            Double minLon,
            Double maxLon,
            int minVisits,
            int limit
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    CASE WHEN s.favorite_id IS NOT NULL THEN 'favorite' ELSE 'geocoding' END AS type,
                    COALESCE(s.favorite_id, s.geocoding_id) AS placeId,
                    MAX(s.location_name) AS locationName,
                    COUNT(s.id) AS visitCount,
                    MAX(s.timestamp) AS lastVisit,
                    AVG(ST_Y(s.location)) AS latitude,
                    AVG(ST_X(s.location)) AS longitude,
                    MAX(COALESCE(f.city, g.city)) AS city,
                    MAX(COALESCE(f.country, g.country)) AS country
                FROM timeline_stays s
                LEFT JOIN favorite_locations f ON s.favorite_id = f.id
                LEFT JOIN reverse_geocoding_location g ON s.geocoding_id = g.id
                WHERE s.user_id = :userId
                  AND s.location IS NOT NULL
                  AND (s.favorite_id IS NOT NULL OR s.geocoding_id IS NOT NULL)
                """);

        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);

        if (startTime != null) {
            sql.append(" AND s.timestamp >= :startTime");
            params.put("startTime", startTime);
        }
        if (endTime != null) {
            sql.append(" AND s.timestamp <= :endTime");
            params.put("endTime", endTime);
        }

        boolean hasViewport = minLat != null && maxLat != null && minLon != null && maxLon != null;
        if (hasViewport) {
            sql.append(" AND ST_Y(s.location) BETWEEN :minLat AND :maxLat");
            params.put("minLat", minLat);
            params.put("maxLat", maxLat);

            // Leaflet bounds can exceed [-180, 180] due to world wrapping.
            // Normalize and support both world-spanning and antimeridian-crossing views.
            double lonSpan = maxLon - minLon;
            if (lonSpan < 360.0d) {
                double normalizedMinLon = normalizeLongitude(minLon);
                double normalizedMaxLon = normalizeLongitude(maxLon);

                if (normalizedMinLon <= normalizedMaxLon) {
                    sql.append(" AND ST_X(s.location) BETWEEN :minLon AND :maxLon");
                    params.put("minLon", normalizedMinLon);
                    params.put("maxLon", normalizedMaxLon);
                } else {
                    sql.append(" AND (ST_X(s.location) >= :minLon OR ST_X(s.location) <= :maxLon)");
                    params.put("minLon", normalizedMinLon);
                    params.put("maxLon", normalizedMaxLon);
                }
            }
        }

        sql.append("""
                
                GROUP BY
                    CASE WHEN s.favorite_id IS NOT NULL THEN 'favorite' ELSE 'geocoding' END,
                    COALESCE(s.favorite_id, s.geocoding_id)
                HAVING COUNT(s.id) >= :minVisits
                ORDER BY lastVisit DESC
                LIMIT :limit
                """);

        params.put("minVisits", Math.max(1, minVisits));
        params.put("limit", Math.max(1, limit));

        var query = getEntityManager().createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        return query.getResultList();
    }

    private double normalizeLongitude(double longitude) {
        return ((longitude + 180.0d) % 360.0d + 360.0d) % 360.0d - 180.0d;
    }

    /**
     * Get detailed statistics for a specific city.
     * Returns total visits, durations, first/last visit, and unique places.
     *
     * @param userId   user ID
     * @param cityName city name
     * @return Object array with statistics, or null if no data
     */
    public Object[] getCityStatistics(UUID userId, String cityName) {
        String query = """
                SELECT COUNT(s) as totalVisits,
                       SUM(s.stayDuration) as totalDuration,
                       AVG(s.stayDuration) as avgDuration,
                       MIN(s.stayDuration) as minDuration,
                       MAX(s.stayDuration) as maxDuration,
                       MIN(s.timestamp) as firstVisit,
                       MAX(s.timestamp) as lastVisit,
                       COUNT(DISTINCT CASE WHEN f.id IS NOT NULL THEN f.id ELSE g.id END) as uniquePlaces
                FROM TimelineStayEntity s
                LEFT JOIN s.favoriteLocation f
                LEFT JOIN s.geocodingLocation g
                WHERE s.user.id = ?1
                  AND COALESCE(f.city, g.city) = ?2
                """;
        return (Object[]) getEntityManager().createQuery(query)
                .setParameter(1, userId)
                .setParameter(2, cityName)
                .getSingleResult();
    }

    /**
     * Get detailed statistics for a specific country.
     *
     * @param userId      user ID
     * @param countryName country name
     * @return Object array with statistics, or null if no data
     */
    public Object[] getCountryStatistics(UUID userId, String countryName) {
        String query = """
                SELECT COUNT(s) as totalVisits,
                       SUM(s.stayDuration) as totalDuration,
                       AVG(s.stayDuration) as avgDuration,
                       MIN(s.stayDuration) as minDuration,
                       MAX(s.stayDuration) as maxDuration,
                       MIN(s.timestamp) as firstVisit,
                       MAX(s.timestamp) as lastVisit,
                       COUNT(DISTINCT COALESCE(f.city, g.city)) as uniqueCities,
                       COUNT(DISTINCT CASE WHEN f.id IS NOT NULL THEN f.id ELSE g.id END) as uniquePlaces
                FROM TimelineStayEntity s
                LEFT JOIN s.favoriteLocation f
                LEFT JOIN s.geocodingLocation g
                WHERE s.user.id = ?1
                  AND COALESCE(f.country, g.country) = ?2
                """;
        return (Object[]) getEntityManager().createQuery(query)
                .setParameter(1, userId)
                .setParameter(2, countryName)
                .getSingleResult();
    }

    /**
     * Get paginated visits for a specific city.
     *
     * @param userId    user ID
     * @param cityName  city name
     * @param page      zero-based page number
     * @param pageSize  number of items per page
     * @param sortBy    field to sort by (default: "timestamp")
     * @param ascending sort direction
     * @return list of timeline stays for the page
     */
    public List<TimelineStayEntity> findByCityPaginated(UUID userId, String cityName,
                                                        int page, int pageSize, String sortBy, boolean ascending) {
        String orderClause = "s." + sortBy + (ascending ? " ASC" : " DESC");
        String query = """
                SELECT s FROM TimelineStayEntity s
                LEFT JOIN s.favoriteLocation f
                LEFT JOIN s.geocodingLocation g
                WHERE s.user.id = ?1
                  AND COALESCE(f.city, g.city) = ?2
                ORDER BY """ + " " + orderClause;

        return getEntityManager().createQuery(query, TimelineStayEntity.class)
                .setParameter(1, userId)
                .setParameter(2, cityName)
                .setFirstResult(page * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
    }

    /**
     * Get paginated visits for a specific country.
     *
     * @param userId      user ID
     * @param countryName country name
     * @param page        zero-based page number
     * @param pageSize    number of items per page
     * @param sortBy      field to sort by (default: "timestamp")
     * @param ascending   sort direction
     * @return list of timeline stays for the page
     */
    public List<TimelineStayEntity> findByCountryPaginated(UUID userId, String countryName,
                                                           int page, int pageSize, String sortBy, boolean ascending) {
        String orderClause = "s." + sortBy + (ascending ? " ASC" : " DESC");
        String query = """
                SELECT s FROM TimelineStayEntity s
                LEFT JOIN FETCH s.favoriteLocation f
                LEFT JOIN FETCH s.geocodingLocation g
                WHERE s.user.id = ?1
                  AND COALESCE(f.country, g.country) = ?2
                ORDER BY """ + " " + orderClause;

        return getEntityManager().createQuery(query, TimelineStayEntity.class)
                .setParameter(1, userId)
                .setParameter(2, countryName)
                .setFirstResult(page * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
    }

    /**
     * Get all visits for a city (for export).
     *
     * @param userId    user ID
     * @param cityName  city name
     * @param sortBy    field to sort by
     * @param ascending sort direction
     * @return list of all timeline stays
     */
    public List<TimelineStayEntity> findByCity(UUID userId, String cityName, String sortBy, boolean ascending) {
        String orderClause = "s." + sortBy + (ascending ? " ASC" : " DESC");
        String query = """
                SELECT s FROM TimelineStayEntity s
                LEFT JOIN s.favoriteLocation f
                LEFT JOIN s.geocodingLocation g
                WHERE s.user.id = ?1
                  AND COALESCE(f.city, g.city) = ?2
                ORDER BY """ + " " + orderClause;

        return getEntityManager().createQuery(query, TimelineStayEntity.class)
                .setParameter(1, userId)
                .setParameter(2, cityName)
                .getResultList();
    }

    /**
     * Get all visits for a country (for export).
     *
     * @param userId      user ID
     * @param countryName country name
     * @param sortBy      field to sort by
     * @param ascending   sort direction
     * @return list of all timeline stays
     */
    public List<TimelineStayEntity> findByCountry(UUID userId, String countryName, String sortBy, boolean ascending) {
        String orderClause = "s." + sortBy + (ascending ? " ASC" : " DESC");
        String query = """
                SELECT s FROM TimelineStayEntity s
                LEFT JOIN FETCH s.favoriteLocation f
                LEFT JOIN FETCH s.geocodingLocation g
                WHERE s.user.id = ?1
                  AND COALESCE(f.country, g.country) = ?2
                ORDER BY """ + " " + orderClause;

        return getEntityManager().createQuery(query, TimelineStayEntity.class)
                .setParameter(1, userId)
                .setParameter(2, countryName)
                .getResultList();
    }

    /**
     * Count total visits for a specific city.
     *
     * @param userId   user ID
     * @param cityName city name
     * @return total count of visits
     */
    public long countByCity(UUID userId, String cityName) {
        String query = """
                SELECT COUNT(s) FROM TimelineStayEntity s
                LEFT JOIN s.favoriteLocation f
                LEFT JOIN s.geocodingLocation g
                WHERE s.user.id = ?1
                  AND COALESCE(f.city, g.city) = ?2
                """;
        return (Long) getEntityManager().createQuery(query)
                .setParameter(1, userId)
                .setParameter(2, cityName)
                .getSingleResult();
    }

    /**
     * Count total visits for a specific country.
     *
     * @param userId      user ID
     * @param countryName country name
     * @return total count of visits
     */
    public long countByCountry(UUID userId, String countryName) {
        String query = """
                SELECT COUNT(s) FROM TimelineStayEntity s
                LEFT JOIN s.favoriteLocation f
                LEFT JOIN s.geocodingLocation g
                WHERE s.user.id = ?1
                  AND COALESCE(f.country, g.country) = ?2
                """;
        return (Long) getEntityManager().createQuery(query)
                .setParameter(1, userId)
                .setParameter(2, countryName)
                .getSingleResult();
    }

    /**
     * Get top places in a specific city (by visit count).
     *
     * @param userId   user ID
     * @param cityName city name
     * @param limit    maximum number of places to return
     * @return list of Object arrays containing place data
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> getTopPlacesInCity(UUID userId, String cityName, int limit) {
        String sql = """
                SELECT CASE WHEN f.id IS NOT NULL THEN 'favorite' ELSE 'geocoding' END as type,
                       CASE WHEN f.id IS NOT NULL THEN f.id ELSE g.id END as placeId,
                       s.location_name as placeName,
                       COUNT(s.id) as visitCount,
                       SUM(s.stay_duration) as totalDuration,
                       AVG(ST_Y(s.location)) as latitude,
                       AVG(ST_X(s.location)) as longitude
                FROM timeline_stays s
                LEFT JOIN favorite_locations f ON s.favorite_id = f.id
                LEFT JOIN reverse_geocoding_location g ON s.geocoding_id = g.id
                WHERE s.user_id = ?1
                  AND COALESCE(f.city, g.city) = ?2
                  AND s.location_name IS NOT NULL
                GROUP BY CASE WHEN f.id IS NOT NULL THEN 'favorite' ELSE 'geocoding' END,
                         CASE WHEN f.id IS NOT NULL THEN f.id ELSE g.id END,
                         s.location_name
                ORDER BY visitCount DESC
                LIMIT ?3
                """;
        return getEntityManager().createNativeQuery(sql)
                .setParameter(1, userId)
                .setParameter(2, cityName)
                .setParameter(3, limit)
                .getResultList();
    }

    /**
     * Get top places in a specific country (by visit count).
     *
     * @param userId      user ID
     * @param countryName country name
     * @param limit       maximum number of places to return
     * @return list of Object arrays containing place data
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> getTopPlacesInCountry(UUID userId, String countryName, int limit) {
        String sql = """
                SELECT CASE WHEN f.id IS NOT NULL THEN 'favorite' ELSE 'geocoding' END as type,
                       CASE WHEN f.id IS NOT NULL THEN f.id ELSE g.id END as placeId,
                       s.location_name as placeName,
                       COUNT(s.id) as visitCount,
                       SUM(s.stay_duration) as totalDuration,
                       AVG(ST_Y(s.location)) as latitude,
                       AVG(ST_X(s.location)) as longitude
                FROM timeline_stays s
                LEFT JOIN favorite_locations f ON s.favorite_id = f.id
                LEFT JOIN reverse_geocoding_location g ON s.geocoding_id = g.id
                WHERE s.user_id = ?1
                  AND COALESCE(f.country, g.country) = ?2
                  AND s.location_name IS NOT NULL
                GROUP BY CASE WHEN f.id IS NOT NULL THEN 'favorite' ELSE 'geocoding' END,
                         CASE WHEN f.id IS NOT NULL THEN f.id ELSE g.id END,
                         s.location_name
                ORDER BY visitCount DESC
                LIMIT ?3
                """;
        return getEntityManager().createNativeQuery(sql)
                .setParameter(1, userId)
                .setParameter(2, countryName)
                .setParameter(3, limit)
                .getResultList();
    }

    /**
     * Get all cities in a specific country.
     *
     * @param userId      user ID
     * @param countryName country name
     * @return list of Object arrays containing city data
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> getCitiesInCountry(UUID userId, String countryName) {
        String query = """
                SELECT COALESCE(f.city, g.city) as cityName,
                       COUNT(s) as visitCount,
                       SUM(s.stayDuration) as totalDuration,
                       COUNT(DISTINCT CASE WHEN f.id IS NOT NULL THEN f.id ELSE g.id END) as uniquePlaces
                FROM TimelineStayEntity s
                LEFT JOIN s.favoriteLocation f
                LEFT JOIN s.geocodingLocation g
                WHERE s.user.id = ?1
                  AND COALESCE(f.country, g.country) = ?2
                  AND COALESCE(f.city, g.city) IS NOT NULL
                GROUP BY COALESCE(f.city, g.city)
                ORDER BY visitCount DESC
                """;
        return getEntityManager().createQuery(query)
                .setParameter(1, userId)
                .setParameter(2, countryName)
                .getResultList();
    }

    /**
     * Get centroid coordinates for every visited city in a country.
     *
     * @return rows containing [cityName, latitude, longitude]
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> getCityCentroidsInCountry(UUID userId, String countryName) {
        String sql = """
                SELECT COALESCE(f.city, g.city) AS city_name,
                       AVG(ST_Y(s.location)) AS latitude,
                       AVG(ST_X(s.location)) AS longitude
                FROM timeline_stays s
                LEFT JOIN favorite_locations f ON s.favorite_id = f.id
                LEFT JOIN reverse_geocoding_location g ON s.geocoding_id = g.id
                WHERE s.user_id = ?1
                  AND COALESCE(f.country, g.country) = ?2
                  AND COALESCE(f.city, g.city) IS NOT NULL
                GROUP BY COALESCE(f.city, g.city)
                """;
        return getEntityManager().createNativeQuery(sql)
                .setParameter(1, userId)
                .setParameter(2, countryName)
                .getResultList();
    }

    /**
     * Get centroid coordinates for a city (average of all visit locations).
     *
     * @param userId   user ID
     * @param cityName city name
     * @return Object array with [latitude, longitude]
     */
    public Object[] getCityCentroid(UUID userId, String cityName) {
        String sql = """
                SELECT AVG(ST_Y(s.location)) as latitude,
                       AVG(ST_X(s.location)) as longitude
                FROM timeline_stays s
                LEFT JOIN favorite_locations f ON s.favorite_id = f.id
                LEFT JOIN reverse_geocoding_location g ON s.geocoding_id = g.id
                WHERE s.user_id = ?1
                  AND COALESCE(f.city, g.city) = ?2
                """;
        return (Object[]) getEntityManager().createNativeQuery(sql)
                .setParameter(1, userId)
                .setParameter(2, cityName)
                .getSingleResult();
    }

    /**
     * Get centroid coordinates for a country (average of all visit locations).
     *
     * @param userId      user ID
     * @param countryName country name
     * @return Object array with [latitude, longitude]
     */
    public Object[] getCountryCentroid(UUID userId, String countryName) {
        String sql = """
                SELECT AVG(ST_Y(s.location)) as latitude,
                       AVG(ST_X(s.location)) as longitude
                FROM timeline_stays s
                LEFT JOIN favorite_locations f ON s.favorite_id = f.id
                LEFT JOIN reverse_geocoding_location g ON s.geocoding_id = g.id
                WHERE s.user_id = ?1
                  AND COALESCE(f.country, g.country) = ?2
                """;
        return (Object[]) getEntityManager().createNativeQuery(sql)
                .setParameter(1, userId)
                .setParameter(2, countryName)
                .getSingleResult();
    }

    /**
     * Get country name for a specific city.
     *
     * @param userId   user ID
     * @param cityName city name
     * @return country name
     */
    public String getCountryForCity(UUID userId, String cityName) {
        String query = """
                SELECT COALESCE(f.country, g.country)
                FROM TimelineStayEntity s
                LEFT JOIN s.favoriteLocation f
                LEFT JOIN s.geocodingLocation g
                WHERE s.user.id = ?1
                  AND COALESCE(f.city, g.city) = ?2
                """;
        List<String> results = getEntityManager().createQuery(query, String.class)
                .setParameter(1, userId)
                .setParameter(2, cityName)
                .setMaxResults(1)
                .getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Search cities by name (for autocomplete/search).
     *
     * @param userId user ID
     * @param query  search query
     * @param limit  maximum number of results
     * @return list of Object arrays containing [cityName, country, visitCount]
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> searchCitiesByName(UUID userId, String query, int limit) {
        String jpql = """
                SELECT COALESCE(f.city, g.city) as city,
                       COALESCE(f.country, g.country) as country,
                       COUNT(s) as visitCount
                FROM TimelineStayEntity s
                LEFT JOIN s.favoriteLocation f
                LEFT JOIN s.geocodingLocation g
                WHERE s.user.id = ?1
                  AND LOWER(COALESCE(f.city, g.city)) LIKE LOWER(?2)
                GROUP BY COALESCE(f.city, g.city), COALESCE(f.country, g.country)
                ORDER BY visitCount DESC
                """;
        return getEntityManager().createQuery(jpql)
                .setParameter(1, userId)
                .setParameter(2, "%" + query + "%")
                .setMaxResults(limit)
                .getResultList();
    }

    /**
     * Search countries by name (for autocomplete/search).
     *
     * @param userId user ID
     * @param query  search query
     * @param limit  maximum number of results
     * @return list of Object arrays containing [countryName, visitCount]
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> searchCountriesByName(UUID userId, String query, int limit) {
        String jpql = """
                SELECT COALESCE(f.country, g.country) as country,
                       COUNT(s) as visitCount
                FROM TimelineStayEntity s
                LEFT JOIN s.favoriteLocation f
                LEFT JOIN s.geocodingLocation g
                WHERE s.user.id = ?1
                  AND LOWER(COALESCE(f.country, g.country)) LIKE LOWER(?2)
                GROUP BY COALESCE(f.country, g.country)
                ORDER BY visitCount DESC
                """;
        return getEntityManager().createQuery(jpql)
                .setParameter(1, userId)
                .setParameter(2, "%" + query + "%")
                .setMaxResults(limit)
                .getResultList();
    }

    /**
     * Get time-based visit counts for a specific city.
     *
     * @param userId   user ID
     * @param cityName city name
     * @return Object array containing [visitsThisWeek, visitsThisMonth, visitsThisYear]
     */
    public Object[] getVisitCountsByCity(UUID userId, String cityName) {
        Instant now = Instant.now();
        Instant weekAgo = now.minus(7, java.time.temporal.ChronoUnit.DAYS);
        Instant monthAgo = now.minus(30, java.time.temporal.ChronoUnit.DAYS);
        Instant yearAgo = now.minus(365, java.time.temporal.ChronoUnit.DAYS);

        String query = """
                SELECT COUNT(s) FROM TimelineStayEntity s
                LEFT JOIN s.favoriteLocation f
                LEFT JOIN s.geocodingLocation g
                WHERE s.user.id = ?1
                  AND COALESCE(f.city, g.city) = ?2
                  AND s.timestamp >= ?3
                """;

        long visitsThisWeek = (Long) getEntityManager().createQuery(query)
                .setParameter(1, userId)
                .setParameter(2, cityName)
                .setParameter(3, weekAgo)
                .getSingleResult();

        long visitsThisMonth = (Long) getEntityManager().createQuery(query)
                .setParameter(1, userId)
                .setParameter(2, cityName)
                .setParameter(3, monthAgo)
                .getSingleResult();

        long visitsThisYear = (Long) getEntityManager().createQuery(query)
                .setParameter(1, userId)
                .setParameter(2, cityName)
                .setParameter(3, yearAgo)
                .getSingleResult();

        return new Object[]{visitsThisWeek, visitsThisMonth, visitsThisYear};
    }

    /**
     * Get time-based visit counts for a specific country.
     *
     * @param userId      user ID
     * @param countryName country name
     * @return Object array containing [visitsThisWeek, visitsThisMonth, visitsThisYear]
     */
    public Object[] getVisitCountsByCountry(UUID userId, String countryName) {
        Instant now = Instant.now();
        Instant weekAgo = now.minus(7, java.time.temporal.ChronoUnit.DAYS);
        Instant monthAgo = now.minus(30, java.time.temporal.ChronoUnit.DAYS);
        Instant yearAgo = now.minus(365, java.time.temporal.ChronoUnit.DAYS);

        String query = """
                SELECT COUNT(s) FROM TimelineStayEntity s
                LEFT JOIN s.favoriteLocation f
                LEFT JOIN s.geocodingLocation g
                WHERE s.user.id = ?1
                  AND COALESCE(f.country, g.country) = ?2
                  AND s.timestamp >= ?3
                """;

        long visitsThisWeek = (Long) getEntityManager().createQuery(query)
                .setParameter(1, userId)
                .setParameter(2, countryName)
                .setParameter(3, weekAgo)
                .getSingleResult();

        long visitsThisMonth = (Long) getEntityManager().createQuery(query)
                .setParameter(1, userId)
                .setParameter(2, countryName)
                .setParameter(3, monthAgo)
                .getSingleResult();

        long visitsThisYear = (Long) getEntityManager().createQuery(query)
                .setParameter(1, userId)
                .setParameter(2, countryName)
                .setParameter(3, yearAgo)
                .getSingleResult();

        return new Object[]{visitsThisWeek, visitsThisMonth, visitsThisYear};
    }
}
