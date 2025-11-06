package org.github.tess1o.geopulse.streaming.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.ai.model.AITimelineTripDTO;
import org.github.tess1o.geopulse.ai.model.AITripStatsDTO;
import org.github.tess1o.geopulse.ai.model.TripGroupBy;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for timeline trip persistence operations.
 */
@ApplicationScoped
public class TimelineTripRepository implements PanacheRepository<TimelineTripEntity> {

    /**
     * Find timeline trips for a user within a date range.
     *
     * @param userId user ID
     * @param from start date (inclusive)
     * @param to end date (inclusive)
     * @return list of timeline trips ordered by timestamp
     */
    public List<TimelineTripEntity> findByUserAndDateRange(UUID userId, Instant from, Instant to) {
        return find("user.id = ?1 and timestamp >= ?2 and timestamp <= ?3 order by timestamp", 
                   userId, from, to).list();
    }

    /**
     * Find timeline trips for a user.
     *
     * @param userId user ID
     * @return list of timeline trips ordered by timestamp
     */
    public List<TimelineTripEntity> findByUser(UUID userId) {
        return find("user.id = ?1 order by timestamp", userId).list();
    }


    /**
     * Find timeline trips for a user within a time range, including boundary expansion.
     * Includes trips that start before the range but extend into it.
     *
     * @param userId user ID
     * @param startTime start of time range
     * @param endTime end of time range
     * @return list of trips that overlap with the time range, ordered by timestamp
     */
    public List<TimelineTripEntity> findByUserIdAndTimeRangeWithExpansion(UUID userId, Instant startTime, Instant endTime) {
        // Find trips that either:
        // 1. Start within the requested range, OR
        // 2. Start before the range but extend into it (boundary expansion)
        return find("user.id = ?1 AND (" +
                   "(timestamp >= ?2 AND timestamp <= ?3) OR " +  // Starts within range
                   "(timestamp < ?2 AND FUNCTION('TIMESTAMPADD', SECOND, tripDuration, timestamp) > ?2)" + // Starts before but extends into range
                   ") ORDER BY timestamp",
                   userId, startTime, endTime).list();
    }

    /**
     * Find AI-optimized timeline trips without GPS path data for a user within a time range.
     * Returns trips with basic information, origin/destination will be populated at service layer.
     * Manual mapping approach for better control.
     * 
     * @param userId    user ID
     * @param startTime start of time range
     * @param endTime   end of time range
     * @return list of AI timeline trips without path data, ordered by timestamp
     */
    public List<AITimelineTripDTO> findAITimelineTripsWithoutPath(UUID userId, Instant startTime, Instant endTime) {
        String query = """
            SELECT t.timestamp,
                   t.tripDuration,
                   t.distanceMeters,
                   t.movementType
            FROM TimelineTripEntity t
            WHERE t.user.id = ?1 AND (
                (t.timestamp >= ?2 AND t.timestamp <= ?3) OR 
                (t.timestamp < ?2 AND FUNCTION('TIMESTAMPADD', SECOND, t.tripDuration, t.timestamp) > ?2)
            )
            ORDER BY t.timestamp
            """;
        
        @SuppressWarnings("unchecked")
        List<Object[]> results = getEntityManager().createQuery(query)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .getResultList();
        
        return results.stream()
                .map(row -> {
                    AITimelineTripDTO dto = new AITimelineTripDTO();
                    dto.setTimestamp(TimestampUtils.getInstantSafe(row[0]));
                    dto.setTripDuration((Long) row[1]);
                    dto.setDistanceMeters((Long) row[2]);
                    dto.setMovementType((String) row[3]);
                    // Origin/destination will be populated by service layer
                    return dto;
                })
                .toList();
    }
    
    /**
     * Get aggregated trip statistics grouped by the specified criteria.
     * Note: Origin/destination grouping is handled at the service layer due to complexity.
     * 
     * @param userId    user ID
     * @param startTime start of time range
     * @param endTime   end of time range
     * @param groupBy   how to group the statistics
     * @return list of trip statistics ordered by count descending
     */
    public List<AITripStatsDTO> findTripStatistics(UUID userId, Instant startTime, Instant endTime, TripGroupBy groupBy) {
        return switch (groupBy) {
            case MOVEMENT_TYPE -> findTripStatsByMovementType(userId, startTime, endTime);
            case DAY -> findTripStatsByDay(userId, startTime, endTime);
            case WEEK -> findTripStatsByWeek(userId, startTime, endTime);
            case MONTH -> findTripStatsByMonth(userId, startTime, endTime);
            case ORIGIN_LOCATION_NAME, DESTINATION_LOCATION_NAME -> {
                // These require service-layer processing due to origin/destination computation complexity
                yield List.of();
            }
        };
    }
    
    private List<AITripStatsDTO> findTripStatsByMovementType(UUID userId, Instant startTime, Instant endTime) {
        String query = """
            SELECT t.movementType,
                   COUNT(*) as tripCount,
                   SUM(t.distanceMeters) as totalDistanceMeters,
                   AVG(t.distanceMeters) as avgDistanceMeters,
                   MIN(t.distanceMeters) as minDistanceMeters,
                   MAX(t.distanceMeters) as maxDistanceMeters,
                   SUM(t.tripDuration) as totalDurationSeconds,
                   AVG(t.tripDuration) as avgDurationSeconds,
                   MIN(t.tripDuration) as minDurationSeconds,
                   MAX(t.tripDuration) as maxDurationSeconds
            FROM TimelineTripEntity t
            WHERE t.user.id = ?1 AND t.timestamp >= ?2 AND t.timestamp <= ?3
              AND t.movementType IS NOT NULL
            GROUP BY t.movementType
            ORDER BY tripCount DESC, totalDistanceMeters DESC
            """;
        
        @SuppressWarnings("unchecked")
        List<Object[]> results = getEntityManager().createQuery(query)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .getResultList();
        
        return results.stream()
                .map(row -> {
                    long totalDistanceMeters = ((Number) row[2]).longValue();
                    long totalDurationSeconds = ((Number) row[6]).longValue();
                    double avgSpeedKmh = totalDurationSeconds > 0 
                        ? (totalDistanceMeters * 3.6) / totalDurationSeconds 
                        : 0.0;
                    
                    return AITripStatsDTO.builder()
                        .groupKey((String) row[0])
                        .groupType("movementType")
                        .tripCount(((Number) row[1]).longValue())
                        .totalDistanceMeters(totalDistanceMeters)
                        .avgDistanceMeters(((Number) row[3]).doubleValue())
                        .minDistanceMeters(((Number) row[4]).longValue())
                        .maxDistanceMeters(((Number) row[5]).longValue())
                        .totalDurationSeconds(totalDurationSeconds)
                        .avgDurationSeconds(((Number) row[7]).doubleValue())
                        .minDurationSeconds(((Number) row[8]).longValue())
                        .maxDurationSeconds(((Number) row[9]).longValue())
                        .avgSpeedKmh(avgSpeedKmh)
                        .build();
                })
                .toList();
    }
    
    private List<AITripStatsDTO> findTripStatsByDay(UUID userId, Instant startTime, Instant endTime) {
        String query = """
            SELECT FUNCTION('DATE', t.timestamp) as day,
                   COUNT(*) as tripCount,
                   SUM(t.distanceMeters) as totalDistanceMeters,
                   AVG(t.distanceMeters) as avgDistanceMeters,
                   MIN(t.distanceMeters) as minDistanceMeters,
                   MAX(t.distanceMeters) as maxDistanceMeters,
                   SUM(t.tripDuration) as totalDurationSeconds,
                   AVG(t.tripDuration) as avgDurationSeconds,
                   MIN(t.tripDuration) as minDurationSeconds,
                   MAX(t.tripDuration) as maxDurationSeconds
            FROM TimelineTripEntity t
            WHERE t.user.id = ?1 AND t.timestamp >= ?2 AND t.timestamp <= ?3
            GROUP BY FUNCTION('DATE', t.timestamp)
            ORDER BY day DESC
            """;
        
        @SuppressWarnings("unchecked")
        List<Object[]> results = getEntityManager().createQuery(query)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .getResultList();
        
        return results.stream()
                .map(row -> {
                    long totalDistanceMeters = ((Number) row[2]).longValue();
                    long totalDurationSeconds = ((Number) row[6]).longValue();
                    double avgSpeedKmh = totalDurationSeconds > 0 
                        ? (totalDistanceMeters * 3.6) / totalDurationSeconds 
                        : 0.0;
                    
                    return AITripStatsDTO.builder()
                        .groupKey(String.valueOf(row[0]))
                        .groupType("day")
                        .tripCount(((Number) row[1]).longValue())
                        .totalDistanceMeters(totalDistanceMeters)
                        .avgDistanceMeters(((Number) row[3]).doubleValue())
                        .minDistanceMeters(((Number) row[4]).longValue())
                        .maxDistanceMeters(((Number) row[5]).longValue())
                        .totalDurationSeconds(totalDurationSeconds)
                        .avgDurationSeconds(((Number) row[7]).doubleValue())
                        .minDurationSeconds(((Number) row[8]).longValue())
                        .maxDurationSeconds(((Number) row[9]).longValue())
                        .avgSpeedKmh(avgSpeedKmh)
                        .build();
                })
                .toList();
    }
    
    private List<AITripStatsDTO> findTripStatsByWeek(UUID userId, Instant startTime, Instant endTime) {
        String query = """
                SELECT to_char(t.timestamp, 'IYYY') || '-W' || to_char(t.timestamp, 'IW') AS week,
                   COUNT(*) as tripCount,
                   SUM(t.distanceMeters) as totalDistanceMeters,
                   AVG(t.distanceMeters) as avgDistanceMeters,
                   MIN(t.distanceMeters) as minDistanceMeters,
                   MAX(t.distanceMeters) as maxDistanceMeters,
                   SUM(t.tripDuration) as totalDurationSeconds,
                   AVG(t.tripDuration) as avgDurationSeconds,
                   MIN(t.tripDuration) as minDurationSeconds,
                   MAX(t.tripDuration) as maxDurationSeconds
            FROM TimelineTripEntity t
            WHERE t.user.id = ?1 AND t.timestamp >= ?2 AND t.timestamp <= ?3
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
                .map(row -> {
                    long totalDistanceMeters = ((Number) row[2]).longValue();
                    long totalDurationSeconds = ((Number) row[6]).longValue();
                    double avgSpeedKmh = totalDurationSeconds > 0 
                        ? (totalDistanceMeters * 3.6) / totalDurationSeconds 
                        : 0.0;
                    
                    return AITripStatsDTO.builder()
                        .groupKey((String) row[0])
                        .groupType("week")
                        .tripCount(((Number) row[1]).longValue())
                        .totalDistanceMeters(totalDistanceMeters)
                        .avgDistanceMeters(((Number) row[3]).doubleValue())
                        .minDistanceMeters(((Number) row[4]).longValue())
                        .maxDistanceMeters(((Number) row[5]).longValue())
                        .totalDurationSeconds(totalDurationSeconds)
                        .avgDurationSeconds(((Number) row[7]).doubleValue())
                        .minDurationSeconds(((Number) row[8]).longValue())
                        .maxDurationSeconds(((Number) row[9]).longValue())
                        .avgSpeedKmh(avgSpeedKmh)
                        .build();
                })
                .toList();
    }
    
    private List<AITripStatsDTO> findTripStatsByMonth(UUID userId, Instant startTime, Instant endTime) {
        String query = """
            SELECT to_char(t.timestamp, 'IYYY-MM') as month,
                   COUNT(*) as tripCount,
                   SUM(t.distanceMeters) as totalDistanceMeters,
                   AVG(t.distanceMeters) as avgDistanceMeters,
                   MIN(t.distanceMeters) as minDistanceMeters,
                   MAX(t.distanceMeters) as maxDistanceMeters,
                   SUM(t.tripDuration) as totalDurationSeconds,
                   AVG(t.tripDuration) as avgDurationSeconds,
                   MIN(t.tripDuration) as minDurationSeconds,
                   MAX(t.tripDuration) as maxDurationSeconds
            FROM TimelineTripEntity t
            WHERE t.user.id = ?1 AND t.timestamp >= ?2 AND t.timestamp <= ?3
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
                .map(row -> {
                    long totalDistanceMeters = ((Number) row[2]).longValue();
                    long totalDurationSeconds = ((Number) row[6]).longValue();
                    double avgSpeedKmh = totalDurationSeconds > 0 
                        ? (totalDistanceMeters * 3.6) / totalDurationSeconds 
                        : 0.0;
                    
                    return AITripStatsDTO.builder()
                        .groupKey((String) row[0])
                        .groupType("month")
                        .tripCount(((Number) row[1]).longValue())
                        .totalDistanceMeters(totalDistanceMeters)
                        .avgDistanceMeters(((Number) row[3]).doubleValue())
                        .minDistanceMeters(((Number) row[4]).longValue())
                        .maxDistanceMeters(((Number) row[5]).longValue())
                        .totalDurationSeconds(totalDurationSeconds)
                        .avgDurationSeconds(((Number) row[7]).doubleValue())
                        .minDurationSeconds(((Number) row[8]).longValue())
                        .maxDurationSeconds(((Number) row[9]).longValue())
                        .avgSpeedKmh(avgSpeedKmh)
                        .build();
                })
                .toList();
    }
    
}