package org.github.tess1o.geopulse.timeline.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.timeline.model.TimelineStayEntity;
import org.locationtech.jts.geom.Geometry;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
     * Find stale timeline stays for a user on a specific date.
     *
     * @param userId user ID
     * @param date   date in UTC
     * @return list of stale timeline stays for that date
     */
    public List<TimelineStayEntity> findStaleByUserAndDate(UUID userId, Instant date) {
        LocalDate localDate = date.atZone(ZoneOffset.UTC).toLocalDate();
        Instant startOfDay = localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = localDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        
        return find("user.id = ?1 and timestamp >= ?2 and timestamp < ?3 and isStale = true order by timestamp",
                userId, startOfDay, endOfDay).list();
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
     * Find timeline stays within a certain distance of a point for a user.
     *
     * @param userId   user ID
     * @param latitude point latitude
     * @param longitude point longitude
     * @param distanceMeters maximum distance in meters
     * @return list of timeline stays within the distance
     */
    public List<TimelineStayEntity> findWithinDistance(UUID userId, double latitude, double longitude, double distanceMeters) {
        return find("user.id = ?1 and ST_DWithin(ST_SetSRID(ST_MakePoint(longitude, latitude), 4326), ST_SetSRID(ST_MakePoint(?3, ?2), 4326), ?4)",
                userId, latitude, longitude, distanceMeters).list();
    }

    /**
     * Mark timeline stays as stale by setting the isStale flag.
     *
     * @param stayIds list of stay IDs to mark as stale
     * @return number of stays updated
     */
    public long markAsStale(List<Long> stayIds) {
        if (stayIds.isEmpty()) {
            return 0;
        }
        return update("isStale = true, lastUpdated = ?1 where id in ?2", Instant.now(), stayIds);
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
}