package org.github.tess1o.geopulse.streaming.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.ai.model.AIStayStatsDTO;
import org.github.tess1o.geopulse.ai.model.AITimelineStayDTO;
import org.github.tess1o.geopulse.ai.model.StayGroupBy;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for timeline stay persistence operations.
 */
@ApplicationScoped
public class TimelineStayRepository implements PanacheRepository<TimelineStayEntity> {

    /**
     * Find timeline stays for a user within a date range.
     *
     * @param userId user ID
     * @param from   start date (inclusive)
     * @param to     end date (inclusive)
     * @return list of timeline stays ordered by timestamp
     */
    public List<TimelineStayEntity> findByUserAndDateRange(UUID userId, Instant from, Instant to) {
        return find("user.id = ?1 and timestamp >= ?2 and timestamp <= ?3 order by timestamp",
                userId, from, to).list();
    }

    /**
     * Find the latest timeline stay for a user that started before the given timestamp.
     * Returns an Optional for better null handling.
     *
     * @param userId          user ID
     * @param beforeTimestamp find stays starting before this timestamp
     * @return Optional containing the most recent stay starting before the given timestamp
     */
    public Optional<TimelineStayEntity> findLatestByUserIdBeforeTimestamp(UUID userId, Instant beforeTimestamp) {
        return find("user.id = ?1 and timestamp < ?2 order by timestamp desc", userId, beforeTimestamp)
                .firstResultOptional();
    }

    /**
     * Find timeline stays for a user within a time range, including boundary expansion.
     * Includes stays that start before the range but extend into it.
     *
     * @param userId    user ID
     * @param startTime start of time range
     * @param endTime   end of time range
     * @return list of stays that overlap with the time range, ordered by timestamp
     */
    public List<TimelineStayEntity> findByUserIdAndTimeRangeWithExpansion(UUID userId, Instant startTime, Instant endTime) {
        // Find stays that either:
        // 1. Start within the requested range, OR
        // 2. Start before the range but extend into it (boundary expansion)
        // Use LEFT JOIN FETCH to eagerly load favoriteLocation and geocodingLocation for city/country data
        String query = """
                SELECT DISTINCT s FROM TimelineStayEntity s
                LEFT JOIN FETCH s.favoriteLocation
                LEFT JOIN FETCH s.geocodingLocation
                WHERE s.user.id = ?1 AND (
                    (s.timestamp >= ?2 AND s.timestamp <= ?3) OR
                    (s.timestamp < ?2 AND FUNCTION('TIMESTAMPADD', SECOND, s.stayDuration, s.timestamp) > ?2)
                )
                ORDER BY s.timestamp
                """;
        return getEntityManager().createQuery(query, TimelineStayEntity.class)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .getResultList();
    }

    /**
     * Find AI-optimized timeline stays with city/country information for a user within a time range.
     * Uses SQL joins to fetch city and country from favorites or geocoding tables.
     * Manual mapping approach for better control.
     *
     * @param userId    user ID
     * @param startTime start of time range
     * @param endTime   end of time range
     * @return list of AI timeline stays with enriched location data, ordered by timestamp
     */
    public List<AITimelineStayDTO> findAITimelineStaysWithLocationData(UUID userId, Instant startTime, Instant endTime) {
        String query = """
                SELECT s.timestamp, s.locationName, s.stayDuration, 
                       COALESCE(f.city, g.city) as city,
                       COALESCE(f.country, g.country) as country
                FROM TimelineStayEntity s
                LEFT JOIN s.favoriteLocation f
                LEFT JOIN s.geocodingLocation g
                WHERE s.user.id = ?1 AND (
                    (s.timestamp >= ?2 AND s.timestamp <= ?3) OR 
                    (s.timestamp < ?2 AND FUNCTION('TIMESTAMPADD', SECOND, s.stayDuration, s.timestamp) > ?2)
                )
                ORDER BY s.timestamp
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> results = getEntityManager().createQuery(query)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .getResultList();

        return results.stream()
                .map(row -> {
                    AITimelineStayDTO dto = new AITimelineStayDTO();
                    dto.setTimestamp(TimestampUtils.getInstantSafe(row[0]));
                    dto.setLocationName((String) row[1]);
                    dto.setStayDurationSeconds((Long) row[2]);
                    dto.setCity((String) row[3]);
                    dto.setCountry((String) row[4]);
                    return dto;
                })
                .toList();
    }

    /**
     * Get aggregated stay statistics grouped by the specified criteria.
     *
     * @param userId    user ID
     * @param startTime start of time range
     * @param endTime   end of time range
     * @param groupBy   how to group the statistics
     * @return list of stay statistics ordered by count descending
     */
    public List<AIStayStatsDTO> findStayStatistics(UUID userId, Instant startTime, Instant endTime, StayGroupBy groupBy) {
        return switch (groupBy) {
            case LOCATION_NAME -> findStayStatsByLocationName(userId, startTime, endTime);
            case CITY -> findStayStatsByCity(userId, startTime, endTime);
            case COUNTRY -> findStayStatsByCountry(userId, startTime, endTime);
            case DAY -> findStayStatsByDay(userId, startTime, endTime);
            case WEEK -> findStayStatsByWeek(userId, startTime, endTime);
            case MONTH -> findStayStatsByMonth(userId, startTime, endTime);
        };
    }

    private List<AIStayStatsDTO> findStayStatsByLocationName(UUID userId, Instant startTime, Instant endTime) {
        String sql = """
                SELECT s.location_name as groupKey,
                       'locationName' as groupType,
                       COUNT(*) as stayCount,
                       SUM(s.stay_duration) as totalDurationSeconds,
                       AVG(s.stay_duration) as avgDurationSeconds,
                       MIN(s.stay_duration) as minDurationSeconds,
                       MAX(s.stay_duration) as maxDurationSeconds,
                       COUNT(DISTINCT COALESCE(f.city, g.city)) as uniqueCityCount,
                       1 as uniqueLocationCount,
                       COUNT(DISTINCT COALESCE(f.country, g.country)) as uniqueCountryCount,
                       MIN(s.timestamp) as firstStayStart,
                       s.location_name as dominantLocation
                FROM timeline_stays s
                LEFT JOIN favorite_locations f ON s.favorite_id = f.id
                LEFT JOIN reverse_geocoding_location g ON s.geocoding_id = g.id
                WHERE s.user_id = ? AND s.timestamp >= ? AND s.timestamp <= ?
                  AND s.location_name IS NOT NULL
                GROUP BY s.location_name
                ORDER BY stayCount DESC, totalDurationSeconds DESC
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> results = getEntityManager().createNativeQuery(sql)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .getResultList();

        return results.stream()
                .map(row -> AIStayStatsDTO.builder()
                        .groupKey((String) row[0])
                        .groupType((String) row[1])
                        .stayCount(((Number) row[2]).longValue())
                        .totalDurationSeconds(((Number) row[3]).longValue())
                        .avgDurationSeconds(((Number) row[4]).doubleValue())
                        .minDurationSeconds(((Number) row[5]).longValue())
                        .maxDurationSeconds(((Number) row[6]).longValue())
                        .uniqueCityCount(((Number) row[7]).longValue())
                        .uniqueLocationCount(((Number) row[8]).longValue())
                        .uniqueCountryCount(((Number) row[9]).longValue())
                        .firstStayStart(TimestampUtils.getInstantSafe(row[10]))
                        .dominantLocation((String) row[11])
                        .build())
                .toList();
    }

    private List<AIStayStatsDTO> findStayStatsByCity(UUID userId, Instant startTime, Instant endTime) {
        String sql = """
                WITH city_groups AS (
                    SELECT COALESCE(f.city, g.city) as city_key,
                           s.user_id,
                           COUNT(*) as stayCount,
                           SUM(s.stay_duration) as totalDurationSeconds,
                           AVG(s.stay_duration) as avgDurationSeconds,
                           MIN(s.stay_duration) as minDurationSeconds,
                           MAX(s.stay_duration) as maxDurationSeconds,
                           1 as uniqueCityCount,
                           COUNT(DISTINCT s.location_name) as uniqueLocationCount,
                           COUNT(DISTINCT COALESCE(f.country, g.country)) as uniqueCountryCount,
                           MIN(s.timestamp) as firstStayStart
                    FROM timeline_stays s
                    LEFT JOIN favorite_locations f ON s.favorite_id = f.id
                    LEFT JOIN reverse_geocoding_location g ON s.geocoding_id = g.id
                    WHERE s.user_id = ? AND s.timestamp >= ? AND s.timestamp <= ?
                      AND COALESCE(f.city, g.city) IS NOT NULL
                    GROUP BY COALESCE(f.city, g.city), s.user_id
                ),
                dominant_locations AS (
                    SELECT COALESCE(f.city, g.city) as city_key,
                           s.location_name,
                           SUM(s.stay_duration) as total_duration,
                           ROW_NUMBER() OVER (PARTITION BY COALESCE(f.city, g.city) ORDER BY SUM(s.stay_duration) DESC) as rn
                    FROM timeline_stays s
                    LEFT JOIN favorite_locations f ON s.favorite_id = f.id
                    LEFT JOIN reverse_geocoding_location g ON s.geocoding_id = g.id
                    WHERE s.user_id = ? AND s.timestamp >= ? AND s.timestamp <= ?
                      AND COALESCE(f.city, g.city) IS NOT NULL
                    GROUP BY COALESCE(f.city, g.city), s.location_name
                )
                SELECT cg.city_key as groupKey,
                       'city' as groupType,
                       cg.stayCount,
                       cg.totalDurationSeconds,
                       cg.avgDurationSeconds,
                       cg.minDurationSeconds,
                       cg.maxDurationSeconds,
                       cg.uniqueCityCount,
                       cg.uniqueLocationCount,
                       cg.uniqueCountryCount,
                       cg.firstStayStart,
                       dl.location_name as dominantLocation
                FROM city_groups cg
                LEFT JOIN dominant_locations dl ON cg.city_key = dl.city_key AND dl.rn = 1
                ORDER BY cg.stayCount DESC, cg.totalDurationSeconds DESC
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> results = getEntityManager().createNativeQuery(sql)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .setParameter(4, userId)
                .setParameter(5, startTime)
                .setParameter(6, endTime)
                .getResultList();

        return results.stream()
                .map(row -> AIStayStatsDTO.builder()
                        .groupKey((String) row[0])
                        .groupType((String) row[1])
                        .stayCount(((Number) row[2]).longValue())
                        .totalDurationSeconds(((Number) row[3]).longValue())
                        .avgDurationSeconds(((Number) row[4]).doubleValue())
                        .minDurationSeconds(((Number) row[5]).longValue())
                        .maxDurationSeconds(((Number) row[6]).longValue())
                        .uniqueCityCount(((Number) row[7]).longValue())
                        .uniqueLocationCount(((Number) row[8]).longValue())
                        .uniqueCountryCount(((Number) row[9]).longValue())
                        .firstStayStart(TimestampUtils.getInstantSafe(row[10]))
                        .dominantLocation((String) row[11])
                        .build())
                .toList();
    }

    private List<AIStayStatsDTO> findStayStatsByCountry(UUID userId, Instant startTime, Instant endTime) {
        String sql = """
                WITH country_groups AS (
                    SELECT COALESCE(f.country, g.country) as country_key,
                           s.user_id,
                           COUNT(*) as stayCount,
                           SUM(s.stay_duration) as totalDurationSeconds,
                           AVG(s.stay_duration) as avgDurationSeconds,
                           MIN(s.stay_duration) as minDurationSeconds,
                           MAX(s.stay_duration) as maxDurationSeconds,
                           COUNT(DISTINCT COALESCE(f.city, g.city)) as uniqueCityCount,
                           COUNT(DISTINCT s.location_name) as uniqueLocationCount,
                           1 as uniqueCountryCount,
                           MIN(s.timestamp) as firstStayStart
                    FROM timeline_stays s
                    LEFT JOIN favorite_locations f ON s.favorite_id = f.id
                    LEFT JOIN reverse_geocoding_location g ON s.geocoding_id = g.id
                    WHERE s.user_id = ? AND s.timestamp >= ? AND s.timestamp <= ?
                      AND COALESCE(f.country, g.country) IS NOT NULL
                    GROUP BY COALESCE(f.country, g.country), s.user_id
                ),
                dominant_locations AS (
                    SELECT COALESCE(f.country, g.country) as country_key,
                           s.location_name,
                           SUM(s.stay_duration) as total_duration,
                           ROW_NUMBER() OVER (PARTITION BY COALESCE(f.country, g.country) ORDER BY SUM(s.stay_duration) DESC) as rn
                    FROM timeline_stays s
                    LEFT JOIN favorite_locations f ON s.favorite_id = f.id
                    LEFT JOIN reverse_geocoding_location g ON s.geocoding_id = g.id
                    WHERE s.user_id = ? AND s.timestamp >= ? AND s.timestamp <= ?
                      AND COALESCE(f.country, g.country) IS NOT NULL
                    GROUP BY COALESCE(f.country, g.country), s.location_name
                )
                SELECT cg.country_key as groupKey,
                       'country' as groupType,
                       cg.stayCount,
                       cg.totalDurationSeconds,
                       cg.avgDurationSeconds,
                       cg.minDurationSeconds,
                       cg.maxDurationSeconds,
                       cg.uniqueCityCount,
                       cg.uniqueLocationCount,
                       cg.uniqueCountryCount,
                       cg.firstStayStart,
                       dl.location_name as dominantLocation
                FROM country_groups cg
                LEFT JOIN dominant_locations dl ON cg.country_key = dl.country_key AND dl.rn = 1
                ORDER BY cg.stayCount DESC, cg.totalDurationSeconds DESC
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> results = getEntityManager().createNativeQuery(sql)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .setParameter(4, userId)
                .setParameter(5, startTime)
                .setParameter(6, endTime)
                .getResultList();

        return results.stream()
                .map(row -> AIStayStatsDTO.builder()
                        .groupKey((String) row[0])
                        .groupType((String) row[1])
                        .stayCount(((Number) row[2]).longValue())
                        .totalDurationSeconds(((Number) row[3]).longValue())
                        .avgDurationSeconds(((Number) row[4]).doubleValue())
                        .minDurationSeconds(((Number) row[5]).longValue())
                        .maxDurationSeconds(((Number) row[6]).longValue())
                        .uniqueCityCount(((Number) row[7]).longValue())
                        .uniqueLocationCount(((Number) row[8]).longValue())
                        .uniqueCountryCount(((Number) row[9]).longValue())
                        .firstStayStart(TimestampUtils.getInstantSafe(row[10]))
                        .dominantLocation((String) row[11])
                        .build())
                .toList();
    }

    private List<AIStayStatsDTO> findStayStatsByDay(UUID userId, Instant startTime, Instant endTime) {
        String sql = """
                WITH day_groups AS (
                    SELECT DATE(s.timestamp) as day_key,
                           s.user_id,
                           COUNT(*) as stayCount,
                           SUM(s.stay_duration) as totalDurationSeconds,
                           AVG(s.stay_duration) as avgDurationSeconds,
                           MIN(s.stay_duration) as minDurationSeconds,
                           MAX(s.stay_duration) as maxDurationSeconds,
                           COUNT(DISTINCT COALESCE(f.city, g.city)) as uniqueCityCount,
                           COUNT(DISTINCT s.location_name) as uniqueLocationCount,
                           COUNT(DISTINCT COALESCE(f.country, g.country)) as uniqueCountryCount,
                           MIN(s.timestamp) as firstStayStart
                    FROM timeline_stays s
                    LEFT JOIN favorite_locations f ON s.favorite_id = f.id
                    LEFT JOIN reverse_geocoding_location g ON s.geocoding_id = g.id
                    WHERE s.user_id = ? AND s.timestamp >= ? AND s.timestamp <= ?
                    GROUP BY DATE(s.timestamp), s.user_id
                ),
                dominant_locations AS (
                    SELECT DATE(s.timestamp) as day_key,
                           s.location_name,
                           SUM(s.stay_duration) as total_duration,
                           ROW_NUMBER() OVER (PARTITION BY DATE(s.timestamp) ORDER BY SUM(s.stay_duration) DESC) as rn
                    FROM timeline_stays s
                    WHERE s.user_id = ? AND s.timestamp >= ? AND s.timestamp <= ?
                    GROUP BY DATE(s.timestamp), s.location_name
                )
                SELECT dg.day_key as groupKey,
                       'day' as groupType,
                       dg.stayCount,
                       dg.totalDurationSeconds,
                       dg.avgDurationSeconds,
                       dg.minDurationSeconds,
                       dg.maxDurationSeconds,
                       dg.uniqueCityCount,
                       dg.uniqueLocationCount,
                       dg.uniqueCountryCount,
                       dg.firstStayStart,
                       dl.location_name as dominantLocation
                FROM day_groups dg
                LEFT JOIN dominant_locations dl ON dg.day_key = dl.day_key AND dl.rn = 1
                ORDER BY dg.day_key DESC
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> results = getEntityManager().createNativeQuery(sql)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .setParameter(4, userId)
                .setParameter(5, startTime)
                .setParameter(6, endTime)
                .getResultList();

        return results.stream()
                .map(row -> AIStayStatsDTO.builder()
                        .groupKey(String.valueOf(row[0]))
                        .groupType((String) row[1])
                        .stayCount(((Number) row[2]).longValue())
                        .totalDurationSeconds(((Number) row[3]).longValue())
                        .avgDurationSeconds(((Number) row[4]).doubleValue())
                        .minDurationSeconds(((Number) row[5]).longValue())
                        .maxDurationSeconds(((Number) row[6]).longValue())
                        .uniqueCityCount(((Number) row[7]).longValue())
                        .uniqueLocationCount(((Number) row[8]).longValue())
                        .uniqueCountryCount(((Number) row[9]).longValue())
                        .firstStayStart(TimestampUtils.getInstantSafe(row[10]))
                        .dominantLocation((String) row[11])
                        .build())
                .toList();
    }

    private List<AIStayStatsDTO> findStayStatsByWeek(UUID userId, Instant startTime, Instant endTime) {
        String sql = """
                WITH week_groups AS (
                    SELECT to_char(s.timestamp, 'IYYY') || '-W' || to_char(s.timestamp, 'IW') as week_key,
                           s.user_id,
                           COUNT(*) as stayCount,
                           SUM(s.stay_duration) as totalDurationSeconds,
                           AVG(s.stay_duration) as avgDurationSeconds,
                           MIN(s.stay_duration) as minDurationSeconds,
                           MAX(s.stay_duration) as maxDurationSeconds,
                           COUNT(DISTINCT COALESCE(f.city, g.city)) as uniqueCityCount,
                           COUNT(DISTINCT s.location_name) as uniqueLocationCount,
                           COUNT(DISTINCT COALESCE(f.country, g.country)) as uniqueCountryCount,
                           MIN(s.timestamp) as firstStayStart
                    FROM timeline_stays s
                    LEFT JOIN favorite_locations f ON s.favorite_id = f.id
                    LEFT JOIN reverse_geocoding_location g ON s.geocoding_id = g.id
                    WHERE s.user_id = ? AND s.timestamp >= ? AND s.timestamp <= ?
                    GROUP BY to_char(s.timestamp, 'IYYY') || '-W' || to_char(s.timestamp, 'IW'), s.user_id
                ),
                dominant_locations AS (
                    SELECT to_char(s.timestamp, 'IYYY') || '-W' || to_char(s.timestamp, 'IW') as week_key,
                           s.location_name,
                           SUM(s.stay_duration) as total_duration,
                           ROW_NUMBER() OVER (PARTITION BY to_char(s.timestamp, 'IYYY') || '-W' || to_char(s.timestamp, 'IW') ORDER BY SUM(s.stay_duration) DESC) as rn
                    FROM timeline_stays s
                    WHERE s.user_id = ? AND s.timestamp >= ? AND s.timestamp <= ?
                    GROUP BY to_char(s.timestamp, 'IYYY') || '-W' || to_char(s.timestamp, 'IW'), s.location_name
                )
                SELECT wg.week_key as groupKey,
                       'week' as groupType,
                       wg.stayCount,
                       wg.totalDurationSeconds,
                       wg.avgDurationSeconds,
                       wg.minDurationSeconds,
                       wg.maxDurationSeconds,
                       wg.uniqueCityCount,
                       wg.uniqueLocationCount,
                       wg.uniqueCountryCount,
                       wg.firstStayStart,
                       dl.location_name as dominantLocation
                FROM week_groups wg
                LEFT JOIN dominant_locations dl ON wg.week_key = dl.week_key AND dl.rn = 1
                ORDER BY wg.week_key DESC
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> results = getEntityManager().createNativeQuery(sql)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .setParameter(4, userId)
                .setParameter(5, startTime)
                .setParameter(6, endTime)
                .getResultList();

        return results.stream()
                .map(row -> AIStayStatsDTO.builder()
                        .groupKey((String) row[0])
                        .groupType((String) row[1])
                        .stayCount(((Number) row[2]).longValue())
                        .totalDurationSeconds(((Number) row[3]).longValue())
                        .avgDurationSeconds(((Number) row[4]).doubleValue())
                        .minDurationSeconds(((Number) row[5]).longValue())
                        .maxDurationSeconds(((Number) row[6]).longValue())
                        .uniqueCityCount(((Number) row[7]).longValue())
                        .uniqueLocationCount(((Number) row[8]).longValue())
                        .uniqueCountryCount(((Number) row[9]).longValue())
                        .firstStayStart(TimestampUtils.getInstantSafe(row[10]))
                        .dominantLocation((String) row[11])
                        .build())
                .toList();
    }

    private List<AIStayStatsDTO> findStayStatsByMonth(UUID userId, Instant startTime, Instant endTime) {
        String sql = """
                WITH month_groups AS (
                    SELECT to_char(s.timestamp, 'IYYY-MM') as month_key,
                           s.user_id,
                           COUNT(*) as stayCount,
                           SUM(s.stay_duration) as totalDurationSeconds,
                           AVG(s.stay_duration) as avgDurationSeconds,
                           MIN(s.stay_duration) as minDurationSeconds,
                           MAX(s.stay_duration) as maxDurationSeconds,
                           COUNT(DISTINCT COALESCE(f.city, g.city)) as uniqueCityCount,
                           COUNT(DISTINCT s.location_name) as uniqueLocationCount,
                           COUNT(DISTINCT COALESCE(f.country, g.country)) as uniqueCountryCount,
                           MIN(s.timestamp) as firstStayStart
                    FROM timeline_stays s
                    LEFT JOIN favorite_locations f ON s.favorite_id = f.id
                    LEFT JOIN reverse_geocoding_location g ON s.geocoding_id = g.id
                    WHERE s.user_id = ? AND s.timestamp >= ? AND s.timestamp <= ?
                    GROUP BY to_char(s.timestamp, 'IYYY-MM'), s.user_id
                ),
                dominant_locations AS (
                    SELECT to_char(s.timestamp, 'IYYY-MM') as month_key,
                           s.location_name,
                           SUM(s.stay_duration) as total_duration,
                           ROW_NUMBER() OVER (PARTITION BY to_char(s.timestamp, 'IYYY-MM') ORDER BY SUM(s.stay_duration) DESC) as rn
                    FROM timeline_stays s
                    WHERE s.user_id = ? AND s.timestamp >= ? AND s.timestamp <= ?
                    GROUP BY to_char(s.timestamp, 'IYYY-MM'), s.location_name
                )
                SELECT mg.month_key as groupKey,
                       'month' as groupType,
                       mg.stayCount,
                       mg.totalDurationSeconds,
                       mg.avgDurationSeconds,
                       mg.minDurationSeconds,
                       mg.maxDurationSeconds,
                       mg.uniqueCityCount,
                       mg.uniqueLocationCount,
                       mg.uniqueCountryCount,
                       mg.firstStayStart,
                       dl.location_name as dominantLocation
                FROM month_groups mg
                LEFT JOIN dominant_locations dl ON mg.month_key = dl.month_key AND dl.rn = 1
                ORDER BY mg.month_key DESC
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> results = getEntityManager().createNativeQuery(sql)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .setParameter(4, userId)
                .setParameter(5, startTime)
                .setParameter(6, endTime)
                .getResultList();

        return results.stream()
                .map(row -> AIStayStatsDTO.builder()
                        .groupKey((String) row[0])
                        .groupType((String) row[1])
                        .stayCount(((Number) row[2]).longValue())
                        .totalDurationSeconds(((Number) row[3]).longValue())
                        .avgDurationSeconds(((Number) row[4]).doubleValue())
                        .minDurationSeconds(((Number) row[5]).longValue())
                        .maxDurationSeconds(((Number) row[6]).longValue())
                        .uniqueCityCount(((Number) row[7]).longValue())
                        .uniqueLocationCount(((Number) row[8]).longValue())
                        .uniqueCountryCount(((Number) row[9]).longValue())
                        .firstStayStart(TimestampUtils.getInstantSafe(row[10]))
                        .dominantLocation((String) row[11])
                        .build())
                .toList();
    }

    // Place Details Feature - Paginated queries for specific locations

    /**
     * Find all stays for a specific favorite location with pagination and sorting.
     *
     * @param favoriteId ID of the favorite location
     * @param userId     user ID
     * @param page       zero-based page number
     * @param pageSize   number of items per page
     * @param sortBy     field to sort by (default: "timestamp")
     * @param ascending  sort direction (true for ascending, false for descending)
     * @return list of timeline stays for the page
     */
    public List<TimelineStayEntity> findByFavoriteLocationIdPaginated(
            Long favoriteId, UUID userId, int page, int pageSize, String sortBy, boolean ascending) {
        String orderClause = sortBy + (ascending ? " asc" : " desc");
        return find("favoriteLocation.id = ?1 and user.id = ?2 order by " + orderClause,
                favoriteId, userId)
                .page(page, pageSize)
                .list();
    }

    /**
     * Find all stays for a specific geocoding location with pagination and sorting.
     *
     * @param geocodingId ID of the geocoding location
     * @param userId      user ID
     * @param page        zero-based page number
     * @param pageSize    number of items per page
     * @param sortBy      field to sort by (default: "timestamp")
     * @param ascending   sort direction (true for ascending, false for descending)
     * @return list of timeline stays for the page
     */
    public List<TimelineStayEntity> findByGeocodingLocationIdPaginated(
            Long geocodingId, UUID userId, int page, int pageSize, String sortBy, boolean ascending) {
        String orderClause = sortBy + (ascending ? " asc" : " desc");
        return find("geocodingLocation.id = ?1 and user.id = ?2 order by " + orderClause,
                geocodingId, userId)
                .page(page, pageSize)
                .list();
    }

    /**
     * Find all stays for a specific favorite location with sorting (no pagination).
     *
     * @param favoriteId ID of the favorite location
     * @param userId     user ID
     * @param sortBy     field to sort by (default: "timestamp")
     * @param ascending  sort direction (true for ascending, false for descending)
     * @return list of all timeline stays
     */
    public List<TimelineStayEntity> findByFavoriteLocationId(
            Long favoriteId, UUID userId, String sortBy, boolean ascending) {
        String orderClause = sortBy + (ascending ? " asc" : " desc");
        return find("favoriteLocation.id = ?1 and user.id = ?2 order by " + orderClause,
                favoriteId, userId).list();
    }

    /**
     * Find all stays for a specific geocoding location with sorting (no pagination).
     *
     * @param geocodingId ID of the geocoding location
     * @param userId      user ID
     * @param sortBy      field to sort by (default: "timestamp")
     * @param ascending   sort direction (true for ascending, false for descending)
     * @return list of all timeline stays
     */
    public List<TimelineStayEntity> findByGeocodingLocationId(
            Long geocodingId, UUID userId, String sortBy, boolean ascending) {
        String orderClause = sortBy + (ascending ? " asc" : " desc");
        return find("geocodingLocation.id = ?1 and user.id = ?2 order by " + orderClause,
                geocodingId, userId).list();
    }

    /**
     * Count total stays for a specific favorite location.
     *
     * @param favoriteId ID of the favorite location
     * @param userId     user ID
     * @return total count of stays
     */
    public long countByFavoriteLocationId(Long favoriteId, UUID userId) {
        return count("favoriteLocation.id = ?1 and user.id = ?2", favoriteId, userId);
    }

    /**
     * Count total stays for a specific geocoding location.
     *
     * @param geocodingId ID of the geocoding location
     * @param userId      user ID
     * @return total count of stays
     */
    public long countByGeocodingLocationId(Long geocodingId, UUID userId) {
        return count("geocodingLocation.id = ?1 and user.id = ?2", geocodingId, userId);
    }

    /**
     * Get aggregate statistics for stays at a specific favorite location.
     *
     * @param favoriteId ID of the favorite location
     * @param userId     user ID
     * @return array containing [totalDuration, avgDuration, minDuration, maxDuration, firstVisit, lastVisit]
     */
    public Object[] getStatisticsByFavoriteLocationId(Long favoriteId, UUID userId) {
        String query = """
                SELECT SUM(s.stayDuration), AVG(s.stayDuration), MIN(s.stayDuration),
                       MAX(s.stayDuration), MIN(s.timestamp), MAX(s.timestamp)
                FROM TimelineStayEntity s
                WHERE s.favoriteLocation.id = ?1 AND s.user.id = ?2
                """;
        return (Object[]) getEntityManager().createQuery(query)
                .setParameter(1, favoriteId)
                .setParameter(2, userId)
                .getSingleResult();
    }

    /**
     * Get aggregate statistics for stays at a specific geocoding location.
     *
     * @param geocodingId ID of the geocoding location
     * @param userId      user ID
     * @return array containing [totalDuration, avgDuration, minDuration, maxDuration, firstVisit, lastVisit]
     */
    public Object[] getStatisticsByGeocodingLocationId(Long geocodingId, UUID userId) {
        String query = """
                SELECT SUM(s.stayDuration), AVG(s.stayDuration), MIN(s.stayDuration),
                       MAX(s.stayDuration), MIN(s.timestamp), MAX(s.timestamp)
                FROM TimelineStayEntity s
                WHERE s.geocodingLocation.id = ?1 AND s.user.id = ?2
                """;
        return (Object[]) getEntityManager().createQuery(query)
                .setParameter(1, geocodingId)
                .setParameter(2, userId)
                .getSingleResult();
    }

    /**
     * Get time-based visit counts for a specific favorite location.
     *
     * @param favoriteId ID of the favorite location
     * @param userId     user ID
     * @return array containing [visitsThisWeek, visitsThisMonth, visitsThisYear]
     */
    public Object[] getVisitCountsByFavoriteLocationId(Long favoriteId, UUID userId) {
        Instant now = Instant.now();
        Instant weekAgo = now.minus(7, java.time.temporal.ChronoUnit.DAYS);
        Instant monthAgo = now.minus(30, java.time.temporal.ChronoUnit.DAYS);
        Instant yearAgo = now.minus(365, java.time.temporal.ChronoUnit.DAYS);

        long visitsThisWeek = count("favoriteLocation.id = ?1 and user.id = ?2 and timestamp >= ?3",
                favoriteId, userId, weekAgo);
        long visitsThisMonth = count("favoriteLocation.id = ?1 and user.id = ?2 and timestamp >= ?3",
                favoriteId, userId, monthAgo);
        long visitsThisYear = count("favoriteLocation.id = ?1 and user.id = ?2 and timestamp >= ?3",
                favoriteId, userId, yearAgo);

        return new Object[]{visitsThisWeek, visitsThisMonth, visitsThisYear};
    }

    /**
     * Get time-based visit counts for a specific geocoding location.
     *
     * @param geocodingId ID of the geocoding location
     * @param userId      user ID
     * @return array containing [visitsThisWeek, visitsThisMonth, visitsThisYear]
     */
    public Object[] getVisitCountsByGeocodingLocationId(Long geocodingId, UUID userId) {
        Instant now = Instant.now();
        Instant weekAgo = now.minus(7, java.time.temporal.ChronoUnit.DAYS);
        Instant monthAgo = now.minus(30, java.time.temporal.ChronoUnit.DAYS);
        Instant yearAgo = now.minus(365, java.time.temporal.ChronoUnit.DAYS);

        long visitsThisWeek = count("geocodingLocation.id = ?1 and user.id = ?2 and timestamp >= ?3",
                geocodingId, userId, weekAgo);
        long visitsThisMonth = count("geocodingLocation.id = ?1 and user.id = ?2 and timestamp >= ?3",
                geocodingId, userId, monthAgo);
        long visitsThisYear = count("geocodingLocation.id = ?1 and user.id = ?2 and timestamp >= ?3",
                geocodingId, userId, yearAgo);

        return new Object[]{visitsThisWeek, visitsThisMonth, visitsThisYear};
    }

    /**
     * Update cached location name for all stays associated with a favorite location.
     * This maintains data consistency when a favorite is renamed.
     *
     * @param favoriteId ID of the favorite location
     * @param userId     user ID
     * @param newName    new location name
     * @return number of stays updated
     */
    public int updateLocationNameByFavoriteId(Long favoriteId, UUID userId, String newName) {
        return update("locationName = ?1 where favoriteLocation.id = ?2 and user.id = ?3",
                newName, favoriteId, userId);
    }

    /**
     * Update cached location name for all stays associated with a geocoding location.
     * This maintains data consistency when a geocoding location is updated.
     *
     * @param geocodingId ID of the geocoding location
     * @param userId      user ID
     * @param newName     new location name
     * @return number of stays updated
     */
    public int updateLocationNameByGeocodingId(Long geocodingId, UUID userId, String newName) {
        return update("locationName = ?1 where geocodingLocation.id = ?2 and user.id = ?3",
                newName, geocodingId, userId);
    }

    /**
     * Count timeline stays for a user within a time range (including boundary expansion).
     * Used for checking dataset size before loading full data.
     *
     * @param userId    user ID
     * @param startTime start of time range
     * @param endTime   end of time range
     * @return count of stays that overlap with the time range
     */
    public long countByUserIdAndTimeRange(UUID userId, Instant startTime, Instant endTime) {
        return count("user.id = ?1 AND (" +
                        "(timestamp >= ?2 AND timestamp <= ?3) OR " +
                        "(timestamp < ?2 AND FUNCTION('TIMESTAMPADD', SECOND, stayDuration, timestamp) > ?2)" +
                        ")",
                userId, startTime, endTime);
    }

    public long countByUser(UUID userId) {
        return count("user.id = ?1", userId);
    }

    public void deleteByUserId(UUID userId) {
        delete("user.id = ?1", userId);
    }

    // Location Analytics Feature - City/Country aggregation and search

    /**
     * Get list of all cities with visit counts for a user.
     * Returns city name, country, visit count, total duration, and unique places count.
     *
     * @param userId user ID
     * @return list of Object arrays containing city summary data
     */
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