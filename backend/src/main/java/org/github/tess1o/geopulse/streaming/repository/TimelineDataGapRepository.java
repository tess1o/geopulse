package org.github.tess1o.geopulse.streaming.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing timeline data gap entities.
 */
@ApplicationScoped
public class TimelineDataGapRepository implements PanacheRepositoryBase<TimelineDataGapEntity, Long> {

    /**
     * Find all data gaps for a user within a time range.
     *
     * @param userId    user identifier
     * @param startTime start of the time range (inclusive)
     * @param endTime   end of the time range (inclusive)
     * @return list of data gaps within the range
     */
    public List<TimelineDataGapEntity> findByUserIdAndTimeRange(UUID userId, Instant startTime, Instant endTime) {
        return find("user.id = ?1 and " +
                        "((startTime >= ?2 and startTime <= ?3) or " +
                        " (endTime >= ?2 and endTime <= ?3) or " +
                        " (startTime <= ?2 and endTime >= ?3))",
                userId, startTime, endTime).list();
    }

    public Optional<TimelineDataGapEntity> findLatestByUserId(UUID userId) {
        return find("user.id = ?1 ORDER BY endTime DESC", userId).firstResultOptional();
    }

    /**
     * Find data gaps with boundary expansion - includes gaps that start before range but extend into it.
     *
     * @param userId    user identifier
     * @param startTime start of time range
     * @param endTime   end of time range
     * @return list of data gaps that overlap with the time range
     */
    public List<TimelineDataGapEntity> findByUserIdAndTimeRangeWithExpansion(UUID userId, Instant startTime, Instant endTime) {
        return find("user.id = ?1 and " +
                        "((startTime >= ?2 and startTime <= ?3) or " +  // Gap starts within range
                        " (endTime >= ?2 and endTime <= ?3) or " +       // Gap ends within range
                        " (startTime <= ?2 and endTime >= ?3) or " +     // Gap spans entire range
                        " (startTime < ?2 and endTime > ?2))" +          // Gap starts before but extends into range
                        " order by startTime",
                userId, startTime, endTime).list();
    }

    /**
     * Count timeline data gaps for a user within a time range (including boundary expansion).
     * Used for checking dataset size before loading full data.
     *
     * @param userId    user ID
     * @param startTime start of time range
     * @param endTime   end of time range
     * @return count of data gaps that overlap with the time range
     */
    public long countByUserIdAndTimeRange(UUID userId, Instant startTime, Instant endTime) {
        return count("user.id = ?1 and (" +
                        "(startTime >= ?2 and startTime <= ?3) or " +
                        "(endTime >= ?2 and endTime <= ?3) or " +
                        "(startTime <= ?2 and endTime >= ?3) or " +
                        "(startTime < ?2 and endTime > ?2)" +
                        ")",
                userId, startTime, endTime);
    }

    public long countByUser(UUID userId) {
        return count("user.id = ?1", userId);
    }
}