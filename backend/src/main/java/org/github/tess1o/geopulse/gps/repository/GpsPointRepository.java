package org.github.tess1o.geopulse.gps.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class GpsPointRepository implements PanacheRepository<GpsPointEntity> {
    /**
     * Find GPS points for a specific user within a time period.
     * Results are ordered by timestamp to ensure the path is in chronological order.
     *
     * @param userId    The ID of the user
     * @param startTime The start of the time period
     * @param endTime   The end of the time period
     * @return A list of GPS point entities ordered by timestamp
     */
    public List<GpsPointEntity> findByUserIdAndTimePeriod(UUID userId, Instant startTime, Instant endTime) {
        return list("user.id = ?1 AND timestamp >= ?2 AND timestamp <= ?3 ORDER BY timestamp ASC",
                userId, startTime, endTime);
    }

    public GpsPointEntity findByUserIdLatestGpsPoint(UUID userId) {
        return find("user.id = ?1 ORDER BY timestamp DESC", userId).firstResult();
    }

    /**
     * Find the latest GPS point for a user and source type.
     * Used for location-based duplicate detection.
     *
     * @param userId     The ID of the user
     * @param sourceType The GPS source type
     * @return The latest GPS point for the user and source type, if any
     */
    public Optional<GpsPointEntity> findLatestByUserIdAndSourceType(UUID userId, GpsSourceType sourceType) {
        return find("user.id = ?1 AND sourceType = ?2 ORDER BY timestamp DESC", userId, sourceType)
                .firstResultOptional();
    }

    /**
     * Find GPS points for a user within a date range with pagination and sorting.
     *
     * @param userId    The ID of the user
     * @param startTime The start of the time period
     * @param endTime   The end of the time period
     * @param page      Page number (0-based)
     * @param pageSize  Number of items per page
     * @param sortBy    Field to sort by (timestamp, altitude, battery)
     * @param sortOrder Sort order (asc or desc)
     * @return A list of GPS point entities for the page
     */
    public List<GpsPointEntity> findByUserAndDateRange(UUID userId, Instant startTime, Instant endTime,
                                                        int page, int pageSize, String sortBy, String sortOrder) {
        // Validate sort field to prevent SQL injection
        String validatedSortBy = validateSortField(sortBy);
        String validatedSortOrder = sortOrder.equalsIgnoreCase("asc") ? "ASC" : "DESC";

        String query = String.format("user.id = ?1 AND timestamp >= ?2 AND timestamp <= ?3 ORDER BY %s %s",
                                     validatedSortBy, validatedSortOrder);

        return find(query, userId, startTime, endTime)
                .page(page, pageSize)
                .list();
    }

    /**
     * Validate and map sort field to database column name.
     * Prevents SQL injection by only allowing whitelisted fields.
     *
     * @param sortBy The requested sort field
     * @return The validated database column name
     */
    private String validateSortField(String sortBy) {
        return switch (sortBy.toLowerCase()) {
            case "timestamp" -> "timestamp";
            case "altitude" -> "altitude";
            case "battery" -> "battery";
            case "velocity" -> "velocity";
            case "accuracy" -> "accuracy";
            default -> "timestamp"; // Default to timestamp if invalid field
        };
    }

    /**
     * Find GPS points by user, timestamp and coordinates for duplicate detection.
     * Uses a small time window (5 seconds) and spatial tolerance for near-duplicates.
     *
     * @param user        The user entity
     * @param timestamp   The timestamp to match
     * @param coordinates The coordinates to match
     * @return A list of potential duplicate GPS points
     */
    public List<GpsPointEntity> findByUserAndTimestampAndCoordinates(UserEntity user, Instant timestamp, Point coordinates) {
        // Allow 5 second tolerance for timestamp and small spatial tolerance
        Instant startTime = timestamp.minusSeconds(5);
        Instant endTime = timestamp.plusSeconds(5);

        // Use native SQL query for PostGIS spatial function
        return getEntityManager().createNativeQuery(
                        "SELECT g.* FROM gps_points g WHERE g.user_id = ?1 " +
                                "AND g.timestamp >= ?2 AND g.timestamp <= ?3 " +
                                "AND ST_DWithin(g.coordinates, ?4, 0.00001)",
                        GpsPointEntity.class)
                .setParameter(1, user.getId())
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .setParameter(4, coordinates)
                .getResultList();
    }

    /**
     * Get GPS point summary data in a single optimized query.
     * Returns: [totalCount, todayCount, firstTimestamp, lastTimestamp]
     */
    public Object[] getGpsPointSummaryData(UUID userId, Instant todayStart, Instant todayEnd) {
        return (Object[]) getEntityManager().createNativeQuery(
                        "SELECT " +
                                "  COUNT(*) as total_count, " +
                                "  COUNT(*) FILTER (WHERE timestamp >= :todayStart AND timestamp < :todayEnd) as today_count, " +
                                "  MIN(timestamp) as first_timestamp, " +
                                "  MAX(timestamp) as last_timestamp " +
                                "FROM gps_points " +
                                "WHERE user_id = :userId")
                .setParameter("userId", userId)
                .setParameter("todayStart", todayStart)
                .setParameter("todayEnd", todayEnd)
                .getSingleResult();
    }

    public void deleteByUserId(UUID userId) {
        delete("user.id = ?1", userId);
    }

    public List<GpsPointEntity> findByUserId(UUID userId) {
        return list("user.id = ?1", userId);
    }

    public Optional<GpsPointEntity> findByUniqueKey(UUID userId, Instant timestamp, Point coordinates) {
        return find("user.id = ?1 AND timestamp = ?2 AND coordinates = ?3", userId, timestamp, coordinates)
                .firstResultOptional();
    }

    // =================== LIGHTWEIGHT GPS POINT METHODS FOR PERFORMANCE ===================

    /**
     * Load essential GPS data for timeline processing using projection query.
     * This method provides 80% memory reduction by loading only coordinates, timestamp,
     * accuracy and velocity - avoiding full JPA entity overhead.
     *
     * @param userId        The user ID
     * @param fromTimestamp Start timestamp for data range
     * @return List of lightweight GPS points ordered by timestamp
     */
    public List<GPSPoint> findEssentialDataForTimeline(UUID userId, Instant fromTimestamp) {
        // Use native SQL with PostGIS functions to extract coordinates
        List<Object[]> results = getEntityManager().createNativeQuery(
                        "SELECT gp.timestamp, ST_Y(gp.coordinates) as latitude, ST_X(gp.coordinates) as longitude, " +
                                "COALESCE(gp.velocity, 0.0) / 3.6 as speed, COALESCE(gp.accuracy, 0.0) as accuracy " +
                                "FROM gps_points gp " +
                                "WHERE gp.user_id = :userId AND gp.timestamp >= :fromTimestamp " +
                                "ORDER BY gp.timestamp ASC")
                .setParameter("userId", userId)
                .setParameter("fromTimestamp", fromTimestamp)
                .getResultList();

        return results.stream()
                .map(this::mapToGPSPoint)
                .toList();
    }

    /**
     * Load essential GPS data in chunks for large datasets.
     * Prevents query timeouts and provides better resource management.
     *
     * @param userId        The user ID
     * @param fromTimestamp Start timestamp for data range
     * @param offset        Offset for pagination
     * @param limit         Number of points to fetch
     * @return List of lightweight GPS points for this chunk
     */
    public List<GPSPoint> findEssentialDataChunk(UUID userId, Instant fromTimestamp,
                                                 int offset, int limit) {
        List<Object[]> results = getEntityManager().createNativeQuery(
                        "SELECT gp.timestamp, ST_Y(gp.coordinates) as latitude, ST_X(gp.coordinates) as longitude, " +
                                "COALESCE(gp.velocity, 0.0) / 3.6 as speed, COALESCE(gp.accuracy, 0.0) as accuracy " +
                                "FROM gps_points gp " +
                                "WHERE gp.user_id = :userId AND gp.timestamp >= :fromTimestamp " +
                                "ORDER BY gp.timestamp ASC " +
                                "LIMIT :limit OFFSET :offset")
                .setParameter("userId", userId)
                .setParameter("fromTimestamp", fromTimestamp)
                .setParameter("limit", limit)
                .setParameter("offset", offset)
                .getResultList();

        return results.stream()
                .map(this::mapToGPSPoint)
                .toList();
    }

    /**
     * Load context points before timeline regeneration start time.
     * Used to provide algorithm context while keeping memory usage minimal.
     *
     * @param userId          The user ID
     * @param beforeTimestamp Get points before this timestamp
     * @param limit           Maximum number of context points to fetch
     * @return List of lightweight GPS points in reverse chronological order
     */
    public List<GPSPoint> findEssentialContextData(UUID userId, Instant beforeTimestamp, int limit) {
        List<Object[]> results = getEntityManager().createNativeQuery(
                        "SELECT gp.timestamp, ST_Y(gp.coordinates) as latitude, ST_X(gp.coordinates) as longitude, " +
                                "COALESCE(gp.velocity, 0.0) / 3.6 as speed, COALESCE(gp.accuracy, 0.0) as accuracy " +
                                "FROM gps_points gp " +
                                "WHERE gp.user_id = :userId AND gp.timestamp < :beforeTimestamp " +
                                "ORDER BY gp.timestamp DESC " +
                                "LIMIT :limit")
                .setParameter("userId", userId)
                .setParameter("beforeTimestamp", beforeTimestamp)
                .setParameter("limit", limit)
                .getResultList();

        return results.stream()
                .map(this::mapToGPSPoint)
                .toList();
    }

    /**
     * Estimate total count of GPS points for a user from a specific timestamp.
     * Used for memory allocation optimization in chunked loading.
     *
     * @param userId        The user ID
     * @param fromTimestamp Start timestamp
     * @return Estimated count of GPS points
     */
    public Long estimatePointCount(UUID userId, Instant fromTimestamp) {
        Query query = getEntityManager().createQuery(
                "SELECT COUNT(gp) FROM GpsPointEntity gp " +
                        "WHERE gp.user.id = :userId AND gp.timestamp >= :fromTimestamp");

        query.setParameter("userId", userId);
        query.setParameter("fromTimestamp", fromTimestamp);

        return (Long) query.getSingleResult();
    }

    /**
     * Map native SQL result array to GPSPoint object.
     * Expected array: [timestamp, latitude, longitude, speed, accuracy]
     */
    private GPSPoint mapToGPSPoint(Object[] row) {
        Instant timestampInstant = TimestampUtils.getInstantSafe(row[0]);
        Double latitude = ((Number) row[1]).doubleValue();
        Double longitude = ((Number) row[2]).doubleValue();
        Double speed = ((Number) row[3]).doubleValue();
        Double accuracy = ((Number) row[4]).doubleValue();

        return new GPSPoint(
                timestampInstant,
                latitude,
                longitude,
                speed,
                accuracy
        );
    }
}