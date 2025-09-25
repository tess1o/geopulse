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
        return find("user.id = ?1 AND (" +
                        "(timestamp >= ?2 AND timestamp <= ?3) OR " +  // Starts within range
                        "(timestamp < ?2 AND FUNCTION('TIMESTAMPADD', SECOND, stayDuration, timestamp) > ?2)" + // Starts before but extends into range
                        ") ORDER BY timestamp",
                userId, startTime, endTime).list();
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
        String query = """
            SELECT s.locationName,
                   COUNT(*) as stayCount,
                   SUM(s.stayDuration) as totalDurationSeconds,
                   AVG(s.stayDuration) as avgDurationSeconds,
                   MIN(s.stayDuration) as minDurationSeconds,
                   MAX(s.stayDuration) as maxDurationSeconds
            FROM TimelineStayEntity s
            WHERE s.user.id = ?1 AND s.timestamp >= ?2 AND s.timestamp <= ?3
              AND s.locationName IS NOT NULL
            GROUP BY s.locationName
            ORDER BY stayCount DESC, totalDurationSeconds DESC
            """;
        
        @SuppressWarnings("unchecked")
        List<Object[]> results = getEntityManager().createQuery(query)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .getResultList();
        
        return results.stream()
                .map(row -> new AIStayStatsDTO(
                    (String) row[0],           // groupKey (locationName)
                    "locationName",            // groupType
                    ((Number) row[1]).longValue(),    // stayCount
                    ((Number) row[2]).longValue(),    // totalDurationSeconds
                    ((Number) row[3]).doubleValue(),  // avgDurationSeconds
                    ((Number) row[4]).longValue(),    // minDurationSeconds
                    ((Number) row[5]).longValue()     // maxDurationSeconds
                ))
                .toList();
    }
    
    private List<AIStayStatsDTO> findStayStatsByCity(UUID userId, Instant startTime, Instant endTime) {
        String query = """
            SELECT COALESCE(f.city, g.city) as city,
                   COUNT(*) as stayCount,
                   SUM(s.stayDuration) as totalDurationSeconds,
                   AVG(s.stayDuration) as avgDurationSeconds,
                   MIN(s.stayDuration) as minDurationSeconds,
                   MAX(s.stayDuration) as maxDurationSeconds
            FROM TimelineStayEntity s
            LEFT JOIN s.favoriteLocation f
            LEFT JOIN s.geocodingLocation g
            WHERE s.user.id = ?1 AND s.timestamp >= ?2 AND s.timestamp <= ?3
              AND COALESCE(f.city, g.city) IS NOT NULL
            GROUP BY COALESCE(f.city, g.city)
            ORDER BY stayCount DESC, totalDurationSeconds DESC
            """;
        
        @SuppressWarnings("unchecked")
        List<Object[]> results = getEntityManager().createQuery(query)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .getResultList();
        
        return results.stream()
                .map(row -> new AIStayStatsDTO(
                    (String) row[0],           // groupKey (city)
                    "city",                    // groupType
                    ((Number) row[1]).longValue(),    // stayCount
                    ((Number) row[2]).longValue(),    // totalDurationSeconds
                    ((Number) row[3]).doubleValue(),  // avgDurationSeconds
                    ((Number) row[4]).longValue(),    // minDurationSeconds
                    ((Number) row[5]).longValue()     // maxDurationSeconds
                ))
                .toList();
    }
    
    private List<AIStayStatsDTO> findStayStatsByCountry(UUID userId, Instant startTime, Instant endTime) {
        String query = """
            SELECT COALESCE(f.country, g.country) as country,
                   COUNT(*) as stayCount,
                   SUM(s.stayDuration) as totalDurationSeconds,
                   AVG(s.stayDuration) as avgDurationSeconds,
                   MIN(s.stayDuration) as minDurationSeconds,
                   MAX(s.stayDuration) as maxDurationSeconds
            FROM TimelineStayEntity s
            LEFT JOIN s.favoriteLocation f
            LEFT JOIN s.geocodingLocation g
            WHERE s.user.id = ?1 AND s.timestamp >= ?2 AND s.timestamp <= ?3
              AND COALESCE(f.country, g.country) IS NOT NULL
            GROUP BY COALESCE(f.country, g.country)
            ORDER BY stayCount DESC, totalDurationSeconds DESC
            """;
        
        @SuppressWarnings("unchecked")
        List<Object[]> results = getEntityManager().createQuery(query)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .getResultList();
        
        return results.stream()
                .map(row -> new AIStayStatsDTO(
                    (String) row[0],           // groupKey (country)
                    "country",                 // groupType
                    ((Number) row[1]).longValue(),    // stayCount
                    ((Number) row[2]).longValue(),    // totalDurationSeconds
                    ((Number) row[3]).doubleValue(),  // avgDurationSeconds
                    ((Number) row[4]).longValue(),    // minDurationSeconds
                    ((Number) row[5]).longValue()     // maxDurationSeconds
                ))
                .toList();
    }
    
    private List<AIStayStatsDTO> findStayStatsByDay(UUID userId, Instant startTime, Instant endTime) {
        String query = """
            SELECT FUNCTION('DATE', s.timestamp) as day,
                   COUNT(*) as stayCount,
                   SUM(s.stayDuration) as totalDurationSeconds,
                   AVG(s.stayDuration) as avgDurationSeconds,
                   MIN(s.stayDuration) as minDurationSeconds,
                   MAX(s.stayDuration) as maxDurationSeconds
            FROM TimelineStayEntity s
            WHERE s.user.id = ?1 AND s.timestamp >= ?2 AND s.timestamp <= ?3
            GROUP BY FUNCTION('DATE', s.timestamp)
            ORDER BY day DESC
            """;
        
        @SuppressWarnings("unchecked")
        List<Object[]> results = getEntityManager().createQuery(query)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .getResultList();
        
        return results.stream()
                .map(row -> new AIStayStatsDTO(
                    String.valueOf(row[0]),    // groupKey (day as YYYY-MM-DD)
                    "day",                     // groupType
                    ((Number) row[1]).longValue(),    // stayCount
                    ((Number) row[2]).longValue(),    // totalDurationSeconds
                    ((Number) row[3]).doubleValue(),  // avgDurationSeconds
                    ((Number) row[4]).longValue(),    // minDurationSeconds
                    ((Number) row[5]).longValue()     // maxDurationSeconds
                ))
                .toList();
    }
    
    private List<AIStayStatsDTO> findStayStatsByWeek(UUID userId, Instant startTime, Instant endTime) {
        String query = """
             SELECT to_char(s.timestamp, 'IYYY') || '-W' || to_char(t.timestamp, 'IW') AS week,
                   COUNT(*) as stayCount,
                   SUM(s.stayDuration) as totalDurationSeconds,
                   AVG(s.stayDuration) as avgDurationSeconds,
                   MIN(s.stayDuration) as minDurationSeconds,
                   MAX(s.stayDuration) as maxDurationSeconds
            FROM TimelineStayEntity s
            WHERE s.user.id = ?1 AND s.timestamp >= ?2 AND s.timestamp <= ?3
            GROUP BY 1
            ORDER BY week DESC
            """;
        
        @SuppressWarnings("unchecked")
        List<Object[]> results = getEntityManager().createQuery(query)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .getResultList();
        
        return results.stream()
                .map(row -> new AIStayStatsDTO(
                    (String) row[0],           // groupKey (week as YYYY-Www)
                    "week",                    // groupType
                    ((Number) row[1]).longValue(),    // stayCount
                    ((Number) row[2]).longValue(),    // totalDurationSeconds
                    ((Number) row[3]).doubleValue(),  // avgDurationSeconds
                    ((Number) row[4]).longValue(),    // minDurationSeconds
                    ((Number) row[5]).longValue()     // maxDurationSeconds
                ))
                .toList();
    }
    
    private List<AIStayStatsDTO> findStayStatsByMonth(UUID userId, Instant startTime, Instant endTime) {
        String query = """
            SELECT to_char(s.timestamp, 'IYYY-MM') as month,
                   COUNT(*) as stayCount,
                   SUM(s.stayDuration) as totalDurationSeconds,
                   AVG(s.stayDuration) as avgDurationSeconds,
                   MIN(s.stayDuration) as minDurationSeconds,
                   MAX(s.stayDuration) as maxDurationSeconds
            FROM TimelineStayEntity s
            WHERE s.user.id = ?1 AND s.timestamp >= ?2 AND s.timestamp <= ?3
            GROUP BY 1
            ORDER BY month DESC
            """;
        
        @SuppressWarnings("unchecked")
        List<Object[]> results = getEntityManager().createQuery(query)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .getResultList();
        
        return results.stream()
                .map(row -> new AIStayStatsDTO(
                    (String) row[0],           // groupKey (month as YYYY-MM)
                    "month",                   // groupType
                    ((Number) row[1]).longValue(),    // stayCount
                    ((Number) row[2]).longValue(),    // totalDurationSeconds
                    ((Number) row[3]).doubleValue(),  // avgDurationSeconds
                    ((Number) row[4]).longValue(),    // minDurationSeconds
                    ((Number) row[5]).longValue()     // maxDurationSeconds
                ))
                .toList();
    }
    
}