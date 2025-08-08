package org.github.tess1o.geopulse.timeline.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.timeline.model.TimelineTripEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
     * @param minDistanceKm minimum distance in kilometers
     * @return list of trips meeting the distance criteria
     */
    public List<TimelineTripEntity> findByUserAndMinDistance(UUID userId, double minDistanceKm) {
        return find("user.id = ?1 and distanceKm >= ?2 order by timestamp", userId, minDistanceKm).list();
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
     * @param newDurationMinutes new duration in minutes
     * @return number of rows updated (should be 1 if successful)
     */
    public long updateEndTimeAndDuration(Long tripId, Instant newEndTime, long newDurationMinutes) {
        return update("tripDuration = ?1, lastUpdated = ?2 where id = ?3", 
                     newDurationMinutes, Instant.now(), tripId);
    }
}