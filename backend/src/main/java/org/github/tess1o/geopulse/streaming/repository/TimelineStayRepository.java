package org.github.tess1o.geopulse.streaming.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.gps.service.simplification.TimelineSegmentBoundary;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.locationtech.jts.geom.Geometry;

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
     * The latest stay starting before the timestamp that timeline generation produces itself: stays created by a
     * manual override (a data gap or part of a trip turned into a stay) are skipped, since generation only recreates
     * them afterwards.
     */
    public Optional<TimelineStayEntity> findLatestGeneratedStayBefore(UUID userId, Instant beforeTimestamp) {
        return getEntityManager().createQuery("""
                        SELECT s FROM TimelineStayEntity s
                        WHERE s.user.id = :userId AND s.timestamp < :before
                          AND NOT EXISTS (SELECT 1 FROM TimelineDataGapStayOverrideEntity o WHERE o.stay = s)
                          AND NOT EXISTS (SELECT 1 FROM TimelineTripStaySplitOverrideEntity o WHERE o.stay = s)
                        ORDER BY s.timestamp DESC
                        """, TimelineStayEntity.class)
                .setParameter("userId", userId)
                .setParameter("before", beforeTimestamp)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    public Optional<TimelineStayEntity> findLatestByUserIdAtOrBeforeTimestamp(UUID userId, Instant timestamp) {
        return find("user.id = ?1 and timestamp <= ?2 order by timestamp desc", userId, timestamp)
                .firstResultOptional();
    }

    /** Latest stay starting at or before the timestamp, with its favorite and geocoding place loaded. */
    public Optional<TimelineStayEntity> findLatestWithPlaceAtOrBefore(UUID userId, Instant timestamp) {
        return getEntityManager().createQuery("""
                        SELECT s FROM TimelineStayEntity s
                        LEFT JOIN FETCH s.favoriteLocation
                        LEFT JOIN FETCH s.geocodingLocation
                        WHERE s.user.id = ?1 AND s.timestamp <= ?2
                        ORDER BY s.timestamp DESC
                        """, TimelineStayEntity.class)
                .setParameter(1, userId)
                .setParameter(2, timestamp)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    /** {@code [stay count, distinct place names]} for stays starting in {@code [from, to)}. */
    public long[] countStaysAndPlacesStartedBetween(UUID userId, Instant from, Instant to) {
        Object[] row = getEntityManager().createQuery("""
                        SELECT COUNT(s), COUNT(DISTINCT s.locationName) FROM TimelineStayEntity s
                        WHERE s.user.id = ?1 AND s.timestamp >= ?2 AND s.timestamp < ?3
                        """, Object[].class)
                .setParameter(1, userId)
                .setParameter(2, from)
                .setParameter(3, to)
                .getSingleResult();
        return new long[]{((Number) row[0]).longValue(), ((Number) row[1]).longValue()};
    }

    public Optional<TimelineStayEntity> findEarliestByUserIdAtOrAfterTimestamp(UUID userId, Instant timestamp) {
        return find("user.id = ?1 and timestamp >= ?2 order by timestamp asc", userId, timestamp)
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
     * Get min/max stay window for all stays within a radius from target geometry.
     * Uses geography distance so radius is interpreted in meters.
     *
     * @param userId user ID
     * @param targetGeometry point or polygon geometry to measure proximity against
     * @param radiusMeters search radius in meters
     * @return [minVisitStart, maxVisitEnd, stayCount]
     */
    public Object[] getVisitWindowNearGeometry(UUID userId, Geometry targetGeometry, double radiusMeters) {
        String sql = """
                SELECT MIN(s.timestamp) AS min_visit_start,
                       MAX(s.timestamp + (s.stay_duration * INTERVAL '1 second')) AS max_visit_end,
                       COUNT(s.id) AS stay_count
                FROM timeline_stays s
                WHERE s.user_id = :userId
                  AND s.location IS NOT NULL
                  AND ST_DWithin(
                    s.location::geography,
                    (:targetGeometry)::geography,
                    :radiusMeters
                  )
                """;

        return (Object[]) getEntityManager().createNativeQuery(sql)
                .setParameter("userId", userId)
                .setParameter("targetGeometry", targetGeometry)
                .setParameter("radiusMeters", radiusMeters)
                .getSingleResult();
    }

    /**
     * Find stays near a point, also including stays inside favorite areas that
     * contain the point. The final column contains the matching area id, when
     * applicable. A stay may occur more than once when favorite areas overlap.
     *
     * Row shape:
     * [id, timestamp, duration, locationName, latitude, longitude,
     *  favoriteId, geocodingId, distanceMeters, matchedAreaId]
     */
    public List<Object[]> findLocationLookupStays(UUID userId,
                                                    Geometry targetPoint,
                                                    double radiusMeters,
                                                    double areaBoundaryToleranceMeters) {
        String sql = """
                SELECT s.id,
                       s.timestamp,
                       s.stay_duration,
                       s.location_name,
                       ST_Y(s.location) AS latitude,
                       ST_X(s.location) AS longitude,
                       s.favorite_id,
                       s.geocoding_id,
                       ST_Distance(s.location::geography, (:targetPoint)::geography) AS distance_meters,
                       area.id AS matched_area_id
                FROM timeline_stays s
                LEFT JOIN favorite_locations area
                  ON area.user_id = :userId
                 AND area.type = 'AREA'
                 AND (
                       ST_Covers(area.geometry, s.location)
                       OR ST_DWithin(
                           ST_Boundary(area.geometry)::geography,
                           s.location::geography,
                           :areaBoundaryToleranceMeters
                       )
                     )
                WHERE s.user_id = :userId
                  AND (
                       ST_DWithin(
                           s.location::geography,
                           (:targetPoint)::geography,
                           :radiusMeters
                       )
                       OR (
                           area.id IS NOT NULL
                           AND (
                               ST_Covers(area.geometry, (:targetPoint)::geometry)
                               OR ST_DWithin(
                                   ST_Boundary(area.geometry)::geography,
                                   (:targetPoint)::geography,
                                   :areaBoundaryToleranceMeters
                               )
                           )
                       )
                  )
                ORDER BY distance_meters ASC, s.timestamp DESC
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> results = getEntityManager().createNativeQuery(sql)
                .setParameter("userId", userId)
                .setParameter("targetPoint", targetPoint)
                .setParameter("radiusMeters", radiusMeters)
                .setParameter("areaBoundaryToleranceMeters", areaBoundaryToleranceMeters)
                .getResultList();
        return results;
    }

    /**
     * Find the nearest recorded stays for the fallback state of a location
     * lookup. The spatial KNN operator uses the existing location index; the
     * returned distance is still calculated in meters for display.
     *
     * Row shape:
     * [id, timestamp, duration, locationName, latitude, longitude,
     *  favoriteId, geocodingId, distanceMeters]
     */
    public List<Object[]> findNearestLocationLookupStays(UUID userId, Geometry targetPoint, int limit) {
        String sql = """
                SELECT s.id,
                       s.timestamp,
                       s.stay_duration,
                       s.location_name,
                       ST_Y(s.location) AS latitude,
                       ST_X(s.location) AS longitude,
                       s.favorite_id,
                       s.geocoding_id,
                       ST_Distance(s.location::geography, (:targetPoint)::geography) AS distance_meters
                FROM timeline_stays s
                WHERE s.user_id = :userId
                ORDER BY s.location <-> (:targetPoint)::geometry
                LIMIT :limit
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> results = getEntityManager().createNativeQuery(sql)
                .setParameter("userId", userId)
                .setParameter("targetPoint", targetPoint)
                .setParameter("limit", limit)
                .getResultList();
        return results;
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
     * Get visit pattern facts for stays at a specific favorite location.
     *
     * @param favoriteId ID of the favorite location
     * @param userId     user ID
     * @param timezone   user timezone for local day/time calculations
     * @return [dayOfWeek, dayVisitCount, arrivalPeriod, periodVisitCount, averageDaysBetweenVisits]
     */
    public Object[] getVisitPatternsByFavoriteLocationId(Long favoriteId, UUID userId, String timezone) {
        return getVisitPatternsByPlaceColumn("favorite_id", favoriteId, userId, timezone);
    }

    /**
     * Get visit pattern facts for stays at a specific geocoding location.
     *
     * @param geocodingId ID of the geocoding location
     * @param userId      user ID
     * @param timezone    user timezone for local day/time calculations
     * @return [dayOfWeek, dayVisitCount, arrivalPeriod, periodVisitCount, averageDaysBetweenVisits]
     */
    public Object[] getVisitPatternsByGeocodingLocationId(Long geocodingId, UUID userId, String timezone) {
        return getVisitPatternsByPlaceColumn("geocoding_id", geocodingId, userId, timezone);
    }

    private Object[] getVisitPatternsByPlaceColumn(String placeColumn, Long placeId, UUID userId, String timezone) {
        String sql = """
                WITH base AS (
                    SELECT s.timestamp
                    FROM timeline_stays s
                    WHERE s.user_id = :userId
                      AND s.%s = :placeId
                ),
                day_counts AS (
                    SELECT EXTRACT(ISODOW FROM timestamp AT TIME ZONE :timezone)::int AS day_of_week,
                           COUNT(*) AS visit_count
                    FROM base
                    GROUP BY day_of_week
                    ORDER BY visit_count DESC, day_of_week ASC
                    LIMIT 1
                ),
                arrival_counts AS (
                    SELECT arrival_period,
                           COUNT(*) AS visit_count
                    FROM (
                        SELECT CASE
                            WHEN local_hour >= 5 AND local_hour < 12 THEN 'Morning'
                            WHEN local_hour >= 12 AND local_hour < 17 THEN 'Afternoon'
                            WHEN local_hour >= 17 AND local_hour < 22 THEN 'Evening'
                            ELSE 'Night'
                        END AS arrival_period
                        FROM (
                            SELECT EXTRACT(HOUR FROM timestamp AT TIME ZONE :timezone)::int AS local_hour
                            FROM base
                        ) local_hours
                    ) periods
                    GROUP BY arrival_period
                    ORDER BY visit_count DESC,
                             CASE arrival_period
                                 WHEN 'Morning' THEN 1
                                 WHEN 'Afternoon' THEN 2
                                 WHEN 'Evening' THEN 3
                                 ELSE 4
                             END ASC
                    LIMIT 1
                ),
                visit_gaps AS (
                    SELECT EXTRACT(EPOCH FROM (
                               timestamp - LAG(timestamp) OVER (ORDER BY timestamp)
                           )) / 86400.0 AS gap_days
                    FROM base
                ),
                cadence AS (
                    SELECT AVG(gap_days) AS average_days_between_visits
                    FROM visit_gaps
                    WHERE gap_days IS NOT NULL
                )
                SELECT day_counts.day_of_week,
                       day_counts.visit_count,
                       arrival_counts.arrival_period,
                       arrival_counts.visit_count,
                       cadence.average_days_between_visits
                FROM day_counts
                CROSS JOIN arrival_counts
                CROSS JOIN cadence
                """.formatted(placeColumn);

        return (Object[]) getEntityManager().createNativeQuery(sql)
                .setParameter("userId", userId)
                .setParameter("placeId", placeId)
                .setParameter("timezone", timezone)
                .getSingleResult();
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

    /**
     * Find lightweight stay segment boundaries for GPS path simplification.
     * Returns only timestamp and duration data, not full stay entities.
     *
     * @param userId    user ID
     * @param startTime start of time range
     * @param endTime   end of time range
     * @return list of stay segment boundaries ordered by timestamp
     */
    public List<TimelineSegmentBoundary> findStaySegmentBoundaries(UUID userId, Instant startTime, Instant endTime) {
        String query = """
                SELECT s.timestamp, s.stayDuration
                FROM TimelineStayEntity s
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
                .map(row -> new TimelineSegmentBoundary(
                        TimestampUtils.getInstantSafe(row[0]),
                        (Long) row[1],
                        TimelineSegmentBoundary.SegmentType.STAY
                ))
                .toList();
    }
}
