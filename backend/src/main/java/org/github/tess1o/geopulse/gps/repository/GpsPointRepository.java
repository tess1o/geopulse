package org.github.tess1o.geopulse.gps.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
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
     * Find the latest GPS timestamp for a user within a specific time range.
     * Used for data gap detection in multi-day timeline processing.
     *
     * @param userId The ID of the user
     * @param startTime The start of the time range
     * @param endTime The end of the time range
     * @return The timestamp of the latest GPS point in the range, or null if none found
     */
    public Instant findLatestTimestamp(UUID userId, Instant startTime, Instant endTime) {
        GpsPointEntity latestPoint = find("user.id = ?1 AND timestamp >= ?2 AND timestamp <= ?3 ORDER BY timestamp DESC",
                userId, startTime, endTime).firstResult();
        return latestPoint != null ? latestPoint.getTimestamp() : null;
    }

    /**
     * Find the latest GPS point for a user and source type.
     * Used for location-based duplicate detection.
     *
     * @param userId The ID of the user
     * @param sourceType The GPS source type
     * @return The latest GPS point for the user and source type, if any
     */
    public Optional<GpsPointEntity> findLatestByUserIdAndSourceType(UUID userId, GpsSourceType sourceType) {
        return find("user.id = ?1 AND sourceType = ?2 ORDER BY timestamp DESC", userId, sourceType)
                .firstResultOptional();
    }

    /**
     * Find GPS points for a user within a date range with pagination.
     *
     * @param userId    The ID of the user
     * @param startTime The start of the time period
     * @param endTime   The end of the time period
     * @param page      Page number (0-based)
     * @param pageSize  Number of items per page
     * @return A list of GPS point entities for the page
     */
    public List<GpsPointEntity> findByUserAndDateRange(UUID userId, Instant startTime, Instant endTime, int page, int pageSize) {
        return find("user.id = ?1 AND timestamp >= ?2 AND timestamp <= ?3 ORDER BY timestamp DESC", userId, startTime, endTime)
                .page(page, pageSize)
                .list();
    }

    public boolean existsByUserAndTimestamp(UUID userId, Instant timestamp) {
        return count("user.id = ?1 AND timestamp = ?2", userId, timestamp) > 0;
    }

    public long deleteByUserAndDateRange(UUID userId, Instant startTime, Instant endTime) {
        return delete("user.id = ?1 AND timestamp >= ?2 AND timestamp <= ?3", userId, startTime, endTime);
    }

    /**
     * Find GPS points by user, timestamp and coordinates for duplicate detection.
     * Uses a small time window (5 seconds) and spatial tolerance for near-duplicates.
     *
     * @param user The user entity
     * @param timestamp The timestamp to match
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
     * Find distinct timestamps (dates) that have GPS data for a user.
     * Used for determining date ranges that need timeline generation after imports.
     */
    public List<Instant> findDistinctTimestampsByUser(UUID userId) {
        return getEntityManager().createQuery(
            "SELECT DISTINCT DATE_TRUNC('day', g.timestamp) FROM GpsPointEntity g WHERE g.user.id = :userId ORDER BY DATE_TRUNC('day', g.timestamp)",
            Instant.class)
            .setParameter("userId", userId)
            .getResultList();
    }
}