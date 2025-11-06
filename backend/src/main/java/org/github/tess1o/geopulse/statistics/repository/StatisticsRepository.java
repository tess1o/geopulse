package org.github.tess1o.geopulse.statistics.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.statistics.model.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for statistics calculations using native SQL queries.
 * This replaces in-memory stream operations with database aggregations for better performance and memory efficiency.
 */
@ApplicationScoped
@Slf4j
public class StatisticsRepository {

    private final EntityManager entityManager;

    public StatisticsRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Get trip aggregations: total distance, total duration, and daily average distance.
     */
    public TripAggregationResult getTripAggregations(UUID userId, Instant startTime, Instant endTime) {
        String sql = """
            WITH daily_distances AS (
                SELECT DATE(timestamp) as day,
                       SUM(distance_meters) as day_distance
                FROM timeline_trips
                WHERE user_id = ?
                  AND timestamp >= ?
                  AND timestamp <= ?
                GROUP BY DATE(timestamp)
            ),
            trip_totals AS (
                SELECT
                    COALESCE(SUM(distance_meters), 0.0) as total_distance,
                    COALESCE(SUM(trip_duration), 0) as total_duration
                FROM timeline_trips
                WHERE user_id = ?
                  AND timestamp >= ?
                  AND timestamp <= ?
            )
            SELECT
                tt.total_distance,
                tt.total_duration,
                COALESCE(AVG(dd.day_distance), 0.0) as daily_avg_distance,
                COUNT(DISTINCT dd.day) as days_with_trips
            FROM trip_totals tt
            LEFT JOIN daily_distances dd ON 1=1
            GROUP BY tt.total_distance, tt.total_duration
            """;

        try {
            Object[] result = (Object[]) entityManager.createNativeQuery(sql)
                    .setParameter(1, userId)
                    .setParameter(2, startTime)
                    .setParameter(3, endTime)
                    .setParameter(4, userId)
                    .setParameter(5, startTime)
                    .setParameter(6, endTime)
                    .getSingleResult();

            return new TripAggregationResult(
                    ((Number) result[0]).doubleValue(),
                    ((Number) result[1]).longValue(),
                    ((Number) result[2]).doubleValue(),
                    ((Number) result[3]).longValue()
            );
        } catch (NoResultException e) {
            log.debug("No trip data found for user {}", userId);
            return new TripAggregationResult(0.0, 0L, 0.0, 0L);
        }
    }

    /**
     * Get count of unique locations visited.
     */
    public long getUniqueLocationsCount(UUID userId, Instant startTime, Instant endTime) {
        String sql = """
            SELECT COUNT(DISTINCT location_name)
            FROM timeline_stays
            WHERE user_id = ?
              AND timestamp >= ?
              AND timestamp <= ?
              AND location_name IS NOT NULL
            """;

        try {
            Number result = (Number) entityManager.createNativeQuery(sql)
                    .setParameter(1, userId)
                    .setParameter(2, startTime)
                    .setParameter(3, endTime)
                    .getSingleResult();
            return result != null ? result.longValue() : 0L;
        } catch (NoResultException e) {
            return 0L;
        }
    }

    /**
     * Get top places by visit count and duration.
     */
    public List<TopPlace> getTopPlaces(UUID userId, Instant startTime, Instant endTime, int limit) {
        String sql = """
            SELECT
                s.location_name,
                COUNT(*) as visits,
                SUM(s.stay_duration) as total_duration,
                AVG(ST_Y(s.location::geometry)) as latitude,
                AVG(ST_X(s.location::geometry)) as longitude
            FROM timeline_stays s
            WHERE s.user_id = ?
              AND s.timestamp >= ?
              AND s.timestamp <= ?
              AND s.location_name IS NOT NULL
            GROUP BY s.location_name
            ORDER BY visits DESC, total_duration DESC
            LIMIT ?
            """;

        @SuppressWarnings("unchecked")
        List<Object[]> results = entityManager.createNativeQuery(sql)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .setParameter(4, limit)
                .getResultList();

        return results.stream()
                .map(row -> TopPlace.builder()
                        .name((String) row[0])
                        .visits(((Number) row[1]).intValue())
                        .duration(((Number) row[2]).longValue())
                        .coordinates(new double[]{
                                ((Number) row[3]).doubleValue(),
                                ((Number) row[4]).doubleValue()
                        })
                        .build())
                .toList();
    }

    /**
     * Get most active day information.
     * Returns the day with the highest total distance traveled.
     */
    public MostActiveDayDto getMostActiveDay(UUID userId, Instant startTime, Instant endTime) {
        String sql = """
            WITH daily_stats AS (
                SELECT
                    DATE(t.timestamp) as day,
                    SUM(t.distance_meters) / 1000.0 as distance_km,
                    SUM(t.trip_duration) as total_trip_duration,
                    COUNT(DISTINCT s.location_name) as locations_visited
                FROM timeline_trips t
                LEFT JOIN timeline_stays s ON s.user_id = t.user_id AND DATE(s.timestamp) = DATE(t.timestamp)
                WHERE t.user_id = ?
                  AND t.timestamp >= ?
                  AND t.timestamp <= ?
                GROUP BY DATE(t.timestamp)
                ORDER BY distance_km DESC
                LIMIT 1
            )
            SELECT
                TO_CHAR(day, 'MM/DD') as date_formatted,
                TO_CHAR(day, 'Day') as day_name,
                distance_km,
                total_trip_duration,
                locations_visited
            FROM daily_stats
            """;

        try {
            Object[] result = (Object[]) entityManager.createNativeQuery(sql)
                    .setParameter(1, userId)
                    .setParameter(2, startTime)
                    .setParameter(3, endTime)
                    .getSingleResult();

            return MostActiveDayDto.builder()
                    .date(((String) result[0]).trim())
                    .day(((String) result[1]).trim())
                    .distanceTraveled(((Number) result[2]).doubleValue())
                    .travelTime(((Number) result[3]).doubleValue())
                    .locationsVisited(((Number) result[4]).longValue())
                    .build();
        } catch (NoResultException e) {
            log.debug("No active days found for user {}", userId);
            return null;
        }
    }

    /**
     * Get chart data grouped by days.
     */
    public List<ChartDataPoint> getChartDataByDays(UUID userId, Instant startTime, Instant endTime, String movementType) {
        String sql = """
            SELECT
                UPPER(TO_CHAR(DATE(timestamp), 'Dy')) as day_label,
                SUM(distance_meters) / 1000.0 as distance_km
            FROM timeline_trips
            WHERE user_id = ?
              AND timestamp >= ?
              AND timestamp <= ?
              AND (CAST(? AS VARCHAR) IS NULL OR movement_type = ? OR movement_type IS NULL OR movement_type = '')
            GROUP BY DATE(timestamp), day_label
            ORDER BY DATE(timestamp)
            """;

        @SuppressWarnings("unchecked")
        List<Object[]> results = entityManager.createNativeQuery(sql)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .setParameter(4, movementType)
                .setParameter(5, movementType)
                .getResultList();

        return results.stream()
                .map(row -> new ChartDataPoint(
                        (String) row[0],
                        ((Number) row[1]).doubleValue()
                ))
                .toList();
    }

    /**
     * Get chart data grouped by weeks (start of week = Monday).
     */
    public List<ChartDataPoint> getChartDataByWeeks(UUID userId, Instant startTime, Instant endTime, String movementType) {
        String sql = """
            SELECT
                TO_CHAR(DATE_TRUNC('week', timestamp::date), 'MM/DD') as week_label,
                SUM(distance_meters) / 1000.0 as distance_km
            FROM timeline_trips
            WHERE user_id = ?
              AND timestamp >= ?
              AND timestamp <= ?
              AND (CAST(? AS VARCHAR) IS NULL OR movement_type = ? OR movement_type IS NULL OR movement_type = '')
            GROUP BY DATE_TRUNC('week', timestamp::date)
            ORDER BY DATE_TRUNC('week', timestamp::date)
            """;

        @SuppressWarnings("unchecked")
        List<Object[]> results = entityManager.createNativeQuery(sql)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .setParameter(4, movementType)
                .setParameter(5, movementType)
                .getResultList();

        return results.stream()
                .map(row -> new ChartDataPoint(
                        (String) row[0],
                        ((Number) row[1]).doubleValue()
                ))
                .toList();
    }

    /**
     * Get route frequencies for the most common routes between stays.
     */
    public List<RouteFrequencyResult> getRouteFrequencies(UUID userId, Instant startTime, Instant endTime) {
        String sql = """
            WITH ordered_stays AS (
                SELECT
                    location_name,
                    timestamp,
                    ROW_NUMBER() OVER (ORDER BY timestamp) as rn
                FROM timeline_stays
                WHERE user_id = ?
                  AND timestamp >= ?
                  AND timestamp <= ?
                  AND location_name IS NOT NULL
            )
            SELECT
                s1.location_name as from_location,
                s2.location_name as to_location,
                COUNT(*) as frequency
            FROM ordered_stays s1
            JOIN ordered_stays s2 ON s2.rn = s1.rn + 1
            GROUP BY s1.location_name, s2.location_name
            ORDER BY frequency DESC
            LIMIT 1
            """;

        @SuppressWarnings("unchecked")
        List<Object[]> results = entityManager.createNativeQuery(sql)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .getResultList();

        return results.stream()
                .map(row -> new RouteFrequencyResult(
                        (String) row[0],
                        (String) row[1],
                        ((Number) row[2]).longValue()
                ))
                .toList();
    }

    /**
     * Get route statistics: average trip duration, unique routes, longest trip.
     */
    public RoutesStatistics getRoutesStatistics(UUID userId, Instant startTime, Instant endTime) {
        // Get basic trip statistics
        String tripStatsSql = """
            SELECT
                AVG(trip_duration) as avg_duration,
                MAX(trip_duration) as max_duration,
                MAX(distance_meters) as max_distance
            FROM timeline_trips
            WHERE user_id = ?
              AND timestamp >= ?
              AND timestamp <= ?
            """;

        Object[] tripStats = (Object[]) entityManager.createNativeQuery(tripStatsSql)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .getSingleResult();

        // Get unique routes count
        String uniqueRoutesSql = """
            WITH ordered_stays AS (
                SELECT
                    location_name,
                    ROW_NUMBER() OVER (ORDER BY timestamp) as rn
                FROM timeline_stays
                WHERE user_id = ?
                  AND timestamp >= ?
                  AND timestamp <= ?
                  AND location_name IS NOT NULL
            )
            SELECT COUNT(DISTINCT (s1.location_name || ' -> ' || s2.location_name))
            FROM ordered_stays s1
            JOIN ordered_stays s2 ON s2.rn = s1.rn + 1
            """;

        Number uniqueRoutesCount = (Number) entityManager.createNativeQuery(uniqueRoutesSql)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .getSingleResult();

        // Get most common route
        List<RouteFrequencyResult> mostCommonRoutes = getRouteFrequencies(userId, startTime, endTime);
        MostCommonRoute mostCommonRoute = mostCommonRoutes.isEmpty()
                ? new MostCommonRoute("", 0)
                : new MostCommonRoute(
                        mostCommonRoutes.get(0).getFromLocation() + " -> " + mostCommonRoutes.get(0).getToLocation(),
                        mostCommonRoutes.get(0).getFrequency().intValue()
                );

        return RoutesStatistics.builder()
                .avgTripDurationSeconds(tripStats[0] != null ? ((Number) tripStats[0]).doubleValue() : 0.0)
                .longestTripDurationSeconds(tripStats[1] != null ? ((Number) tripStats[1]).doubleValue() : 0.0)
                .longestTripDistanceMeters(tripStats[2] != null ? ((Number) tripStats[2]).doubleValue() : 0.0)
                .uniqueRoutesCount(uniqueRoutesCount != null ? uniqueRoutesCount.intValue() : 0)
                .mostCommonRoute(mostCommonRoute)
                .build();
    }
}
