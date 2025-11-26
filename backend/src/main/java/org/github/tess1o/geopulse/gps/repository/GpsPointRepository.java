package org.github.tess1o.geopulse.gps.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.model.GpsPointFilterDTO;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

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


    public Optional<GpsPointEntity> findLatest() {
        return find("ORDER BY timestamp DESC")
                .firstResultOptional();
    }

    public Optional<GpsPointEntity> findLatest(UUID userId) {
        return find("user.id = ?1 ORDER BY timestamp DESC", userId)
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
     * Load essential GPS data in chunks for large datasets.
     * Prevents query timeouts and provides better resource management.
     *
     * @param userId        The user ID
     * @param fromTimestamp Start timestamp for data range
     * @param offset        Offset for pagination
     * @param limit         Number of points to fetch
     * @return List of lightweight GPS points for this chunk
     */
    public List<GPSPoint> findEssentialPointsInInterval(UUID userId, Instant start, Instant end) {
        List<Object[]> results = getEntityManager().createNativeQuery(
                        "SELECT gp.timestamp, ST_Y(gp.coordinates) as latitude, ST_X(gp.coordinates) as longitude, " +
                                "COALESCE(gp.velocity, 0.0) / 3.6 as speed, COALESCE(gp.accuracy, 0.0) as accuracy " +
                                "FROM gps_points gp " +
                                "WHERE gp.user_id = :userId AND gp.timestamp >= :start AND gp.timestamp <= :end " +
                                "ORDER BY gp.timestamp ASC")
                .setParameter("userId", userId)
                .setParameter("start", start)
                .setParameter("end", end)
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

    // =================== FILTERING METHODS ===================

    /**
     * Find GPS points with filters, pagination and sorting.
     *
     * @param userId    The ID of the user
     * @param filters   Filter criteria
     * @param page      Page number (0-based)
     * @param pageSize  Number of items per page
     * @param sortBy    Field to sort by
     * @param sortOrder Sort order (asc or desc)
     * @return A list of GPS point entities for the page
     */
    public List<GpsPointEntity> findByUserAndFilters(UUID userId, GpsPointFilterDTO filters,
                                                     int page, int pageSize, String sortBy, String sortOrder) {
        QueryBuilder queryBuilder = buildFilterQuery(userId, filters);

        // Add sorting
        String validatedSortBy = validateSortField(sortBy);
        String validatedSortOrder = sortOrder.equalsIgnoreCase("asc") ? "ASC" : "DESC";
        queryBuilder.query.append(String.format(" ORDER BY %s %s", validatedSortBy, validatedSortOrder));

        Query query = getEntityManager().createQuery(queryBuilder.query.toString(), GpsPointEntity.class);
        queryBuilder.params.forEach(query::setParameter);

        query.setFirstResult(page * pageSize);
        query.setMaxResults(pageSize);

        return query.getResultList();
    }

    public long countByUser(UUID userId) {
        Query query = getEntityManager().createQuery(
                "SELECT COUNT(gp) FROM GpsPointEntity gp WHERE gp.user.id = :userId");
        query.setParameter("userId", userId);
        return (Long) query.getSingleResult();
    }

    /**
     * Count GPS points matching filters.
     *
     * @param userId  The ID of the user
     * @param filters Filter criteria
     * @return Count of GPS points matching the filters
     */
    public long countByUserAndFilters(UUID userId, GpsPointFilterDTO filters) {
        QueryBuilder queryBuilder = buildFilterQuery(userId, filters);

        // Replace SELECT with COUNT
        String countQuery = "SELECT COUNT(gp) FROM GpsPointEntity gp WHERE " + queryBuilder.whereClause;

        Query query = getEntityManager().createQuery(countQuery, Long.class);
        queryBuilder.params.forEach(query::setParameter);

        return (Long) query.getSingleResult();
    }

    /**
     * Stream GPS points for export in batches to avoid OOM.
     * Processes results in chunks and calls consumer for each batch.
     *
     * @param userId    The ID of the user
     * @param filters   Filter criteria
     * @param batchSize Number of records to process at a time
     * @param consumer  Consumer to process each batch
     */
    public void streamByUserAndFilters(UUID userId, GpsPointFilterDTO filters,
                                       int batchSize, Consumer<List<GpsPointEntity>> consumer) {
        long totalCount = countByUserAndFilters(userId, filters);
        int totalBatches = (int) Math.ceil((double) totalCount / batchSize);

        for (int batch = 0; batch < totalBatches; batch++) {
            List<GpsPointEntity> batchData = findByUserAndFilters(
                    userId, filters, batch, batchSize, "timestamp", "asc");

            if (!batchData.isEmpty()) {
                consumer.accept(batchData);

                // Clear the persistence context to free memory
                getEntityManager().clear();
            }
        }
    }

    /**
     * Build filter query with WHERE clause and parameters.
     * Helper method to construct dynamic queries based on active filters.
     *
     * @param userId  The ID of the user
     * @param filters Filter criteria
     * @return QueryBuilder containing query string and parameters
     */
    private QueryBuilder buildFilterQuery(UUID userId, GpsPointFilterDTO filters) {
        StringBuilder query = new StringBuilder("SELECT gp FROM GpsPointEntity gp WHERE ");
        StringBuilder whereClause = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        // Always filter by user
        whereClause.append("gp.user.id = :userId");
        params.put("userId", userId);

        // If ID filtering is active, ignore all other filters and only filter by IDs
        if (filters.hasIdFilter()) {
            whereClause.append(" AND gp.id IN :gpsPointIds");
            params.put("gpsPointIds", filters.getGpsPointIds());
            query.append(whereClause);
            return new QueryBuilder(query, whereClause.toString(), params);
        }

        // Time range filters
        if (filters.getStartTime() != null) {
            whereClause.append(" AND gp.timestamp >= :startTime");
            params.put("startTime", filters.getStartTime());
        }
        if (filters.getEndTime() != null) {
            whereClause.append(" AND gp.timestamp <= :endTime");
            params.put("endTime", filters.getEndTime());
        }

        // Accuracy filters
        if (filters.getAccuracyMin() != null) {
            whereClause.append(" AND gp.accuracy >= :accuracyMin");
            params.put("accuracyMin", filters.getAccuracyMin());
        }
        if (filters.getAccuracyMax() != null) {
            whereClause.append(" AND gp.accuracy <= :accuracyMax");
            params.put("accuracyMax", filters.getAccuracyMax());
        }

        // Speed filters (velocity is stored in km/h)
        if (filters.getSpeedMin() != null) {
            whereClause.append(" AND gp.velocity >= :speedMin");
            params.put("speedMin", filters.getSpeedMin());
        }
        if (filters.getSpeedMax() != null) {
            whereClause.append(" AND gp.velocity <= :speedMax");
            params.put("speedMax", filters.getSpeedMax());
        }

        // Source type filter
        if (filters.getSourceTypes() != null && !filters.getSourceTypes().isEmpty()) {
            whereClause.append(" AND gp.sourceType IN :sourceTypes");
            params.put("sourceTypes", filters.getSourceTypes());
        }

        query.append(whereClause);

        return new QueryBuilder(query, whereClause.toString(), params);
    }

    /**
     * Helper class to encapsulate query building results.
     */
    private static class QueryBuilder {
        final StringBuilder query;
        final String whereClause;
        final Map<String, Object> params;

        QueryBuilder(StringBuilder query, String whereClause, Map<String, Object> params) {
            this.query = query;
            this.whereClause = whereClause;
            this.params = params;
        }
    }
}