package org.github.tess1o.geopulse.timeline.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineDataSource;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Background service that automatically generates and persists timeline data for all users
 * when a day completes. Runs daily at midnight UTC to process the previous day's data.
 */
@ApplicationScoped
@Slf4j
public class DailyTimelineProcessingService {

    @Inject
    UserRepository userRepository;

    @Inject
    TimelineQueryService timelineQueryService;

    @Inject
    TimelinePersistenceService persistenceService;

    // Configuration properties
    @ConfigProperty(name = "geopulse.timeline.daily-processing.batch-size", defaultValue = "20")
    int batchSize;

    @ConfigProperty(name = "geopulse.timeline.daily-processing.batch-delay-ms", defaultValue = "1000")
    long batchDelayMs;

    @ConfigProperty(name = "geopulse.timeline.daily-processing.enabled", defaultValue = "true")
    boolean processingEnabled;

    /**
     * Daily job that runs at midnight UTC to process the previous day's timeline data
     * for all active users.
     */
    @Scheduled(cron = "0 5 0 * * ?") // Run at 00:05 UTC daily (5 minutes past midnight to ensure day completion)
    public void processPreviousDayTimelines() {
        if (!processingEnabled) {
            log.debug("Daily timeline processing is disabled, skipping");
            return;
        }
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        Instant startOfDay = yesterday.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = yesterday.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        log.info("Starting daily timeline processing for date: {} (UTC)", yesterday);

        try {
            // Get all active users
            List<UserEntity> activeUsers = userRepository.findActiveUsers();
            log.info("Found {} active users for timeline processing", activeUsers.size());

            int totalProcessed = 0;
            int totalSuccessful = 0;
            int totalFailed = 0;

            // Process users in batches to manage memory usage
            for (int i = 0; i < activeUsers.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, activeUsers.size());
                List<UserEntity> batch = activeUsers.subList(i, endIndex);

                log.debug("Processing user batch {}-{} of {}", i + 1, endIndex, activeUsers.size());

                for (UserEntity user : batch) {
                    try {
                        boolean processed = processUserTimeline(user.getId(), startOfDay, endOfDay, yesterday);
                        totalProcessed++;
                        if (processed) {
                            totalSuccessful++;
                        }
                    } catch (Exception e) {
                        totalFailed++;
                        log.error("Failed to process timeline for user {}: {}", user.getId(), e.getMessage(), e);
                    }
                }

                // Small delay between batches to reduce system load
                try {
                    Thread.sleep(batchDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Timeline processing interrupted");
                    break;
                }
            }

            log.info("Daily timeline processing completed for {}: {} users processed, {} successful, {} failed",
                    yesterday, totalProcessed, totalSuccessful, totalFailed);

        } catch (Exception e) {
            log.error("Daily timeline processing failed for date {}: {}", yesterday, e.getMessage(), e);
        }
    }

    /**
     * Process timeline for a single user for the specified day.
     *
     * @param userId     user ID
     * @param startOfDay start of the day (UTC)
     * @param endOfDay   end of the day (UTC)
     * @param date       the date being processed
     * @return true if timeline was generated and persisted, false if skipped
     */
    @Transactional
    public boolean processUserTimeline(UUID userId, Instant startOfDay, Instant endOfDay, LocalDate date) {
        try {
            // Check if timeline is already persisted for this date
            if (persistenceService.hasPersistedTimelineForDate(userId, startOfDay)) {
                log.debug("Timeline already exists for user {} on date {}, skipping", userId, date);
                return false;
            }

            // Generate timeline for the day using smart query service
            log.debug("Generating timeline for user {} on date {}", userId, date);
            MovementTimelineDTO timeline = timelineQueryService.getTimeline(userId, startOfDay, endOfDay);

            if (timeline == null) {
                log.debug("No timeline data generated for user {} on date {}", userId, date);
                return false;
            }

            // Check if there's meaningful data
            if (timeline.getStaysCount() == 0 && timeline.getTripsCount() == 0) {
                log.debug("No timeline activities found for user {} on date {}, skipping", userId, date);
                return false;
            }

            // TimelineQueryService automatically handles persistence for completed past days
            log.debug("Successfully processed timeline for user {} on date {}: {} stays, {} trips (source: {})",
                    userId, date, timeline.getStaysCount(), timeline.getTripsCount(), timeline.getDataSource());

            return timeline.getDataSource() == TimelineDataSource.CACHED; // Return true if data was persisted

        } catch (Exception e) {
            log.error("Error processing timeline for user {} on date {}: {}", userId, date, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Manual trigger for processing a specific date's timelines.
     * Useful for backfilling or reprocessing data.
     *
     * @param targetDate the date to process
     * @return processing statistics
     */
    @Transactional
    public ProcessingStatistics processTimelineForDate(LocalDate targetDate) {
        Instant startOfDay = targetDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = targetDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        log.info("Manual timeline processing triggered for date: {}", targetDate);

        List<UserEntity> activeUsers = userRepository.findActiveUsers();
        int totalProcessed = 0;
        int totalSuccessful = 0;
        int totalFailed = 0;

        for (UserEntity user : activeUsers) {
            try {
                boolean processed = processUserTimeline(user.getId(), startOfDay, endOfDay, targetDate);
                totalProcessed++;
                if (processed) {
                    totalSuccessful++;
                }
            } catch (Exception e) {
                totalFailed++;
                log.error("Failed to process timeline for user {} on date {}: {}", user.getId(), targetDate, e.getMessage());
            }
        }

        ProcessingStatistics stats = new ProcessingStatistics(targetDate, totalProcessed, totalSuccessful, totalFailed);
        log.info("Manual timeline processing completed for {}: {}", targetDate, stats);

        return stats;
    }

    /**
     * Get processing statistics for monitoring.
     */
    public record ProcessingStatistics(LocalDate processedDate, int totalUsers, int successfulUsers, int failedUsers) {

        @Override
        @NonNull
        public String toString() {
            return String.format("ProcessingStatistics{date=%s, total=%d, successful=%d, failed=%d}",
                    processedDate, totalUsers, successfulUsers, failedUsers);
        }
    }
}