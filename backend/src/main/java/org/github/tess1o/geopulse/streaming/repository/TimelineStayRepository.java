package org.github.tess1o.geopulse.streaming.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;

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
        return find("user.id = ?1 AND (" +
                        "(timestamp >= ?2 AND timestamp <= ?3) OR " +  // Starts within range
                        "(timestamp < ?2 AND FUNCTION('TIMESTAMPADD', SECOND, stayDuration, timestamp) > ?2)" + // Starts before but extends into range
                        ") ORDER BY timestamp",
                userId, startTime, endTime).list();
    }
}