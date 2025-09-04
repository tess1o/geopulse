package org.github.tess1o.geopulse.streaming.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
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

    public Optional<TimelineTripEntity> findLatestByUserId(UUID userId) {
        return find("user.id = ?1 ORDER BY timestamp DESC", userId).firstResultOptional();
    }

    /**
     * Find timeline trips for a user on a specific date (UTC).
     *
     * @param userId user ID
     * @param date   date in UTC
     * @return list of timeline trips for that date ordered by timestamp
     */
    public List<TimelineTripEntity> findByUserAndDate(UUID userId, Instant date) {
        LocalDate localDate = date.atZone(ZoneOffset.UTC).toLocalDate();
        Instant startOfDay = localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = localDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        
        return findByUserAndDateRange(userId, startOfDay, endOfDay);
    }

    /**
     * Find stale timeline trips for a user on a specific date.
     *
     * @param userId user ID
     * @param date   date in UTC
     * @return list of stale timeline trips for that date
     */
    public List<TimelineTripEntity> findStaleByUserAndDate(UUID userId, Instant date) {
        LocalDate localDate = date.atZone(ZoneOffset.UTC).toLocalDate();
        Instant startOfDay = localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = localDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        
        return find("user.id = ?1 and timestamp >= ?2 and timestamp < ?3 and isStale = true order by timestamp",
                userId, startOfDay, endOfDay).list();
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
     * Mark timeline trips as stale by setting the isStale flag.
     *
     * @param tripIds list of trip IDs to mark as stale
     * @return number of trips updated
     */
    public long markAsStale(List<Long> tripIds) {
        if (tripIds.isEmpty()) {
            return 0;
        }
        return update("isStale = true, lastUpdated = ?1 where id in ?2", Instant.now(), tripIds);
    }

    /**
     * Delete timeline trips older than the given timestamp.
     *
     * @param cutoffTime entries older than this will be deleted
     * @return number of entries deleted
     */
    public long deleteOlderThan(Instant cutoffTime) {
        return delete("createdAt < ?1", cutoffTime);
    }

    /**
     * Delete timeline trips for dates before a certain date (cleanup).
     * Used for data retention policies.
     *
     * @param userId user ID
     * @param beforeDate delete trips before this date
     * @return number of trips deleted
     */
    public long deleteByUserBeforeDate(UUID userId, Instant beforeDate) {
        return delete("user.id = ?1 and timestamp < ?2", userId, beforeDate);
    }

    /**
     * Count timeline trips for a user.
     *
     * @param userId user ID
     * @return count of timeline trips
     */
    public long countByUser(UUID userId) {
        return count("user.id = ?1", userId);
    }

    /**
     * Find trips by movement type for analysis.
     *
     * @param userId user ID
     * @param movementType movement type (e.g., "WALKING", "DRIVING")
     * @return list of trips with that movement type
     */
    public List<TimelineTripEntity> findByUserAndMovementType(UUID userId, String movementType) {
        return find("user.id = ?1 and movementType = ?2 order by timestamp", userId, movementType).list();
    }

    /**
     * Find trips longer than a specified distance.
     *
     * @param userId user ID
     * @param minDistanceMeters minimum distance in meters
     * @return list of trips meeting the distance criteria
     */
    public List<TimelineTripEntity> findByUserAndMinDistance(UUID userId, long minDistanceMeters) {
        return find("user.id = ?1 and distanceMeters >= ?2 order by timestamp", userId, minDistanceMeters).list();
    }

    public boolean existsByUserAndTimestamp(UUID userId, Instant timestamp) {
        return count("user.id = ?1 AND timestamp = ?2", userId, timestamp) > 0;
    }

    /**
     * Find the latest timeline trip for a user that started before the given timestamp.
     * Used for prepending previous context to timeline requests.
     *
     * @param userId user ID
     * @param beforeTimestamp find trips starting before this timestamp
     * @return the most recent trip starting before the given timestamp, or null if none found
     */
    public TimelineTripEntity findLatestBefore(UUID userId, Instant beforeTimestamp) {
        return find("user.id = ?1 and timestamp < ?2 order by timestamp desc", userId, beforeTimestamp)
                .firstResult();
    }

    /**
     * Update the end time and duration of an existing timeline trip.
     * Used during overnight processing to extend a trip from previous day.
     *
     * @param tripId trip ID to update
     * @param newEndTime new end timestamp for the trip 
     * @param newDurationSeconds new duration in seconds
     * @return number of rows updated (should be 1 if successful)
     */
    public long updateEndTimeAndDuration(Long tripId, Instant newEndTime, long newDurationSeconds) {
        return update("tripDuration = ?1, lastUpdated = ?2 where id = ?3", 
                     newDurationSeconds, Instant.now(), tripId);
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
     * Find timeline trips for a user within a time range (exact range, no expansion).
     * 
     * @param userId user ID
     * @param startTime start of time range
     * @param endTime end of time range
     * @return list of trips within the exact time range, ordered by timestamp
     */
    public List<TimelineTripEntity> findByUserIdAndTimeRange(UUID userId, Instant startTime, Instant endTime) {
        return find("user.id = ?1 AND timestamp >= ?2 AND timestamp <= ?3 ORDER BY timestamp", 
                   userId, startTime, endTime).list();
    }

    /**
     * Delete timeline trips for a user within a specific time range.
     * 
     * @param userId user ID
     * @param startTime start of time range
     * @param endTime end of time range
     * @return number of trips deleted
     */
    public long deleteByUserIdAndTimeRange(UUID userId, Instant startTime, Instant endTime) {
        return delete("user.id = ?1 AND timestamp >= ?2 AND timestamp <= ?3", 
                     userId, startTime, endTime);
    }
}