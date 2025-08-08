package org.github.tess1o.geopulse.timeline.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.timeline.model.TimelineDataGapEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for managing timeline data gap entities.
 */
@ApplicationScoped
public class TimelineDataGapRepository implements PanacheRepositoryBase<TimelineDataGapEntity, Long> {

    /**
     * Find all data gaps for a user within a time range.
     * 
     * @param userId user identifier
     * @param startTime start of the time range (inclusive)
     * @param endTime end of the time range (inclusive)
     * @return list of data gaps within the range
     */
    public List<TimelineDataGapEntity> findByUserIdAndTimeRange(UUID userId, Instant startTime, Instant endTime) {
        return find("user.id = ?1 and " +
                   "((startTime >= ?2 and startTime <= ?3) or " +
                   " (endTime >= ?2 and endTime <= ?3) or " +
                   " (startTime <= ?2 and endTime >= ?3))",
                userId, startTime, endTime).list();
    }

    /**
     * Find all data gaps for a user on specific dates.
     * 
     * @param userId user identifier
     * @param startTime start of the date range
     * @param endTime end of the date range
     * @return list of data gaps
     */
    public List<TimelineDataGapEntity> findByUserIdInRange(UUID userId, Instant startTime, Instant endTime) {
        return find("user.id = ?1 and startTime >= ?2 and endTime <= ?3 order by startTime", 
                userId, startTime, endTime).list();
    }

    /**
     * Delete all data gaps for a user within a specific time range.
     * Used when regenerating timeline data.
     * 
     * @param userId user identifier
     * @param startTime start of the time range
     * @param endTime end of the time range
     * @return number of deleted records
     */
    public long deleteByUserIdAndTimeRange(UUID userId, Instant startTime, Instant endTime) {
        return delete("user.id = ?1 and " +
                     "((startTime >= ?2 and startTime <= ?3) or " +
                     " (endTime >= ?2 and endTime <= ?3) or " +
                     " (startTime <= ?2 and endTime >= ?3))",
                userId, startTime, endTime);
    }

    /**
     * Delete all data gaps for a user.
     * Used for cleanup operations.
     * 
     * @param userId user identifier
     * @return number of deleted records
     */
    public long deleteByUserId(UUID userId) {
        return delete("user.id", userId);
    }

    /**
     * Count data gaps for a user within a time range.
     * 
     * @param userId user identifier
     * @param startTime start of the time range
     * @param endTime end of the time range
     * @return count of data gaps
     */
    public long countByUserIdAndTimeRange(UUID userId, Instant startTime, Instant endTime) {
        return count("user.id = ?1 and " +
                    "((startTime >= ?2 and startTime <= ?3) or " +
                    " (endTime >= ?2 and endTime <= ?3) or " +
                    " (startTime <= ?2 and endTime >= ?3))",
                userId, startTime, endTime);
    }
}