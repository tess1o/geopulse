package org.github.tess1o.geopulse.streaming.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.locationtech.jts.geom.Geometry;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
     * @param from start date (inclusive)
     * @param to end date (inclusive)
     * @return list of timeline stays ordered by timestamp
     */
    public List<TimelineStayEntity> findByUserAndDateRange(UUID userId, Instant from, Instant to) {
        return find("user.id = ?1 and timestamp >= ?2 and timestamp <= ?3 order by timestamp", 
                   userId, from, to).list();
    }

    public Optional<TimelineStayEntity> findLatestByUserId(UUID userId) {
        return find("user.id = ?1 ORDER BY timestamp DESC", userId).firstResultOptional();
    }

    /**
     * Find timeline stays for a user.
     *
     * @param userId user ID
     * @return list of timeline stays ordered by timestamp
     */
    public List<TimelineStayEntity> findByUser(UUID userId) {
        return find("user.id = ?1 order by timestamp", userId).list();
    }

    /**
     * Delete timeline stays older than the given timestamp.
     *
     * @param cutoffTime entries older than this will be deleted
     * @return number of entries deleted
     */
    public long deleteOlderThan(Instant cutoffTime) {
        return delete("createdAt < ?1", cutoffTime);
    }

    /**
     * Count timeline stays for a user.
     *
     * @param userId user ID
     * @return count of timeline stays
     */
    public long countByUser(UUID userId) {
        return count("user.id = ?1", userId);
    }

    /**
     * Find timeline stays for a user on a specific date (UTC).
     *
     * @param userId user ID
     * @param date   date in UTC
     * @return list of timeline stays for that date ordered by timestamp
     */
    public List<TimelineStayEntity> findByUserAndDate(UUID userId, Instant date) {
        LocalDate localDate = date.atZone(ZoneOffset.UTC).toLocalDate();
        Instant startOfDay = localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = localDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        
        return findByUserAndDateRange(userId, startOfDay, endOfDay);
    }

    /**
     * Check if any timeline stays exist for a specific date.
     * Used by DailyTimelineProcessingService to determine if timeline data is already cached.
     * 
     * @param userId user ID
     * @param timelineDate date to check for existing timeline data
     * @return true if timeline stays exist for this date, false otherwise
     */
    public boolean hasPersistedTimelineForDate(UUID userId, Instant timelineDate) {
        List<TimelineStayEntity> stays = findByUserAndDate(userId, timelineDate);
        return !stays.isEmpty();
    }

    /**
     * Find timeline stays that reference a specific favorite location.
     *
     * @param favoriteId favorite location ID
     * @return list of timeline stays that reference this favorite
     */
    public List<TimelineStayEntity> findByFavoriteId(Long favoriteId) {
        return find("favoriteLocation.id = ?1", favoriteId).list();
    }


    /**
     * Delete timeline stays for dates before a certain date (cleanup).
     * Used for data retention policies.
     *
     * @param userId user ID
     * @param beforeDate delete stays before this date
     * @return number of stays deleted
     */
    public long deleteByUserBeforeDate(UUID userId, Instant beforeDate) {
        return delete("user.id = ?1 and timestamp < ?2", userId, beforeDate);
    }

    /**
     * Find timeline stays within a certain distance of a geometry for a user.
     *
     * @param userId       user ID
     * @param geometry     the geometry to search around
     * @param distanceMeters maximum distance in meters
     * @return list of timeline stays within the distance
     */
    public List<TimelineStayEntity> findWithinDistance(UUID userId, Geometry geometry, double distanceMeters) {
        String nativeQuery = """
            SELECT * FROM timeline_stays ts 
            WHERE ts.user_id = ? 
            AND ST_DWithin(
                ST_SetSRID(ST_MakePoint(ts.longitude, ts.latitude), 4326)::geography,
                ?::geography,
                ?
            )
            ORDER BY ts.timestamp
            """;
        return getEntityManager().createNativeQuery(nativeQuery, TimelineStayEntity.class)
                .setParameter(1, userId)
                .setParameter(2, geometry)
                .setParameter(3, distanceMeters)
                .getResultList();
    }

    /**
     * Find timeline stays within or near a favorite area for a user.
     *
     * @param userId       user ID
     * @param areaGeometry the area geometry
     * @param proximityMeters maximum proximity distance in meters
     * @return list of timeline stays within or near the area
     */
    public List<TimelineStayEntity> findWithinOrNearArea(UUID userId, Geometry areaGeometry, double proximityMeters) {
        String nativeQuery = """
            SELECT * FROM timeline_stays ts 
            WHERE ts.user_id = ? 
            AND (
                ST_Within(ST_SetSRID(ST_MakePoint(ts.longitude, ts.latitude), 4326), ?)
                OR ST_DWithin(
                    ST_SetSRID(ST_MakePoint(ts.longitude, ts.latitude), 4326)::geography,
                    ?::geography,
                    ?
                )
            )
            ORDER BY ts.timestamp
            """;
        return getEntityManager().createNativeQuery(nativeQuery, TimelineStayEntity.class)
                .setParameter(1, userId)
                .setParameter(2, areaGeometry)
                .setParameter(3, areaGeometry)
                .setParameter(4, proximityMeters)
                .getResultList();
    }

    public boolean existsByUserAndTimestamp(UUID userId, Instant timestamp) {
        return count("user.id = ?1 AND timestamp = ?2", userId, timestamp) > 0;
    }

    /**
     * Find the latest timeline stay for a user that started before the given timestamp.
     * Used for prepending previous context to timeline requests.
     *
     * @param userId user ID
     * @param beforeTimestamp find stays starting before this timestamp
     * @return the most recent stay starting before the given timestamp, or null if none found
     */
    public TimelineStayEntity findLatestBefore(UUID userId, Instant beforeTimestamp) {
        return find("user.id = ?1 and timestamp < ?2 order by timestamp desc", userId, beforeTimestamp)
                .firstResult();
    }

    /**
     * Find the latest timeline stay for a user that started before the given timestamp.
     * Returns an Optional for better null handling.
     *
     * @param userId user ID
     * @param beforeTimestamp find stays starting before this timestamp
     * @return Optional containing the most recent stay starting before the given timestamp
     */
    public Optional<TimelineStayEntity> findLatestByUserIdBeforeTimestamp(UUID userId, Instant beforeTimestamp) {
        return find("user.id = ?1 and timestamp < ?2 order by timestamp desc", userId, beforeTimestamp)
                .firstResultOptional();
    }

    /**
     * Update the end time and duration of an existing timeline stay.
     * Used during overnight processing to extend a stay from previous day.
     *
     * @param stayId stay ID to update
     * @param newEndTime new end timestamp for the stay 
     * @param newDurationSeconds new duration in seconds
     * @return number of rows updated (should be 1 if successful)
     */
    public long updateEndTimeAndDuration(Long stayId, Instant newEndTime, long newDurationSeconds) {
        return update("stayDuration = ?1, lastUpdated = ?2 where id = ?3", 
                     newDurationSeconds, Instant.now(), stayId);
    }

    /**
     * Find timeline stays for a user within a time range, including boundary expansion.
     * Includes stays that start before the range but extend into it.
     * 
     * @param userId user ID
     * @param startTime start of time range
     * @param endTime end of time range
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
     * Find timeline stays for a user within a time range (exact range, no expansion).
     * 
     * @param userId user ID
     * @param startTime start of time range
     * @param endTime end of time range
     * @return list of stays within the exact time range, ordered by timestamp
     */
    public List<TimelineStayEntity> findByUserIdAndTimeRange(UUID userId, Instant startTime, Instant endTime) {
        return find("user.id = ?1 AND timestamp >= ?2 AND timestamp <= ?3 ORDER BY timestamp", 
                   userId, startTime, endTime).list();
    }

    /**
     * Delete timeline stays for a user within a specific time range.
     * 
     * @param userId user ID
     * @param startTime start of time range
     * @param endTime end of time range
     * @return number of stays deleted
     */
    public long deleteByUserIdAndTimeRange(UUID userId, Instant startTime, Instant endTime) {
        return delete("user.id = ?1 AND timestamp >= ?2 AND timestamp <= ?3", 
                     userId, startTime, endTime);
    }
}