package org.github.tess1o.geopulse.timeline.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineStayEntity;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class TimelineInvalidationService {

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    TimelineRegenerationService regenerationService;

    private final ConcurrentLinkedQueue<QueuedTimelineUpdate> timelineUpdateQueue;
    private final Map<TimelineKey, AtomicInteger> retryCounters;
    private final AtomicInteger totalProcessed;
    private final AtomicInteger totalFailed;
    private volatile boolean processing;

    // Configuration constants
    private static final int MAX_RETRIES = 3;
    private static final int MAX_BATCH_SIZE = 20;
    private static final int MIN_BATCH_SIZE = 1;
    private static final long RETRY_DELAY_MINUTES = 5;

    public TimelineInvalidationService() {
        this.timelineUpdateQueue = new ConcurrentLinkedQueue<>();
        this.retryCounters = new ConcurrentHashMap<>();
        this.totalProcessed = new AtomicInteger(0);
        this.totalFailed = new AtomicInteger(0);
        this.processing = false;
    }

    @Transactional
    public void markStaleAndQueue(List<TimelineStayEntity> stays) {
        if (stays.isEmpty()) {
            log.debug("No stays to mark as stale");
            return;
        }

        log.info("Marking {} timeline stays as stale", stays.size());

        // Mark stays as stale
        stays.forEach(stay -> {
            stay.setIsStale(true);
            stay.setLastUpdated(Instant.now());
        });
        stayRepository.persist(stays);

        // Queue background update jobs - group by user and date
        List<TimelineKey> timelineKeys = stays.stream()
                .map(stay -> new TimelineKey(
                        stay.getUser().getId(),
                        stay.getTimestamp().truncatedTo(ChronoUnit.DAYS)
                ))
                .distinct()
                .collect(Collectors.toList());

        Instant now = Instant.now();
        timelineKeys.forEach(key -> {
            QueuedTimelineUpdate update = new QueuedTimelineUpdate(key, now, 0);
            timelineUpdateQueue.offer(update);
        });

        log.info("Queued {} timeline dates for background regeneration", timelineKeys.size());
    }


    @Scheduled(every = "1s")
    public void checkAndProcessQueue() {
        if (processing || timelineUpdateQueue.isEmpty()) {
            return;
        }

        processing = true;
        try {
            processAvailableWork();
        } finally {
            processing = false;
        }
    }

    private void processAvailableWork() {
        int processed = 0;
        int batchSize = getAdaptiveBatchSize();
        QueuedTimelineUpdate queuedUpdate;
        Instant now = Instant.now();

        // Process up to adaptive batch size timeline updates
        while (processed < batchSize && (queuedUpdate = timelineUpdateQueue.poll()) != null) {
            TimelineKey timelineKey = queuedUpdate.getTimelineKey();

            // Check if this update should be retried yet (delay between retries)
            if (queuedUpdate.getRetryCount() > 0) {
                long minutesSinceQueued = ChronoUnit.MINUTES.between(queuedUpdate.getQueuedAt(), now);
                if (minutesSinceQueued < RETRY_DELAY_MINUTES) {
                    // Too soon to retry, re-queue for later
                    timelineUpdateQueue.offer(queuedUpdate);
                    continue;
                }
            }

            try {
                log.debug("Processing timeline regeneration for user {} on date {} (attempt {})",
                        timelineKey.getUserId(), timelineKey.getDate(), queuedUpdate.getRetryCount() + 1);
                MovementTimelineDTO regeneratedTimeline = regenerationService.regenerateTimeline(timelineKey.getUserId(), timelineKey.getDate());

                if (regeneratedTimeline != null) {
                    // Success - remove from retry tracking and increment success counter
                    retryCounters.remove(timelineKey);
                    totalProcessed.incrementAndGet();

                    log.info("Successfully regenerated timeline for user {} on date {} after {} attempts (result: {} stays, {} trips)",
                            timelineKey.getUserId(), timelineKey.getDate(), queuedUpdate.getRetryCount() + 1,
                            regeneratedTimeline.getStaysCount(), regeneratedTimeline.getTripsCount());
                } else {
                    // Regeneration returned null - treat as error
                    throw new IllegalArgumentException("Timeline regeneration returned null result");
                }
                processed++;
            } catch (Exception e) {
                log.error("Failed to process timeline regeneration for user {} on date {} (attempt {}): {}",
                        timelineKey.getUserId(), timelineKey.getDate(), queuedUpdate.getRetryCount() + 1, e.getMessage(), e);

                // Implement retry logic
                if (queuedUpdate.getRetryCount() < MAX_RETRIES) {
                    QueuedTimelineUpdate retryUpdate = new QueuedTimelineUpdate(
                            timelineKey,
                            now,
                            queuedUpdate.getRetryCount() + 1
                    );
                    timelineUpdateQueue.offer(retryUpdate);

                    AtomicInteger retryCount = retryCounters.computeIfAbsent(timelineKey, k -> new AtomicInteger(0));
                    retryCount.incrementAndGet();

                    log.debug("Re-queued timeline regeneration for retry (attempt {} of {})",
                            retryUpdate.getRetryCount(), MAX_RETRIES + 1);
                } else {
                    // Max retries exceeded
                    retryCounters.remove(timelineKey);
                    totalFailed.incrementAndGet();

                    log.error("Timeline regeneration failed permanently for user {} on date {} after {} attempts",
                            timelineKey.getUserId(), timelineKey.getDate(), MAX_RETRIES + 1);
                }
            }
        }

        if (processed > 0) {
            log.debug("Processed {} timeline regeneration tasks from queue", processed);
        }

        // Log queue statistics periodically
        logQueueStatistics();
    }

    /**
     * Calculate adaptive batch size based on queue depth for optimal throughput.
     * - High queue depth: larger batches to catch up quickly
     * - Medium queue depth: moderate batches for steady processing
     * - Low queue depth: small batches to avoid over-processing
     */
    private int getAdaptiveBatchSize() {
        int queueSize = timelineUpdateQueue.size();

        if (queueSize > 50) {
            return MAX_BATCH_SIZE; // High load: process maximum
        } else if (queueSize > 20) {
            return Math.max(MIN_BATCH_SIZE, MAX_BATCH_SIZE / 2); // Medium load: half max
        } else if (queueSize > 5) {
            return Math.max(MIN_BATCH_SIZE, MAX_BATCH_SIZE / 4); // Low-medium load: quarter max
        } else if (queueSize > 0) {
            return MIN_BATCH_SIZE; // Very low load: minimum batch
        } else {
            return 0; // No work
        }
    }

    /**
     * Log queue statistics for monitoring.
     */
    private void logQueueStatistics() {
        int queueSize = timelineUpdateQueue.size();
        int activeRetries = retryCounters.size();

        if (queueSize > 0 || activeRetries > 0) {
            log.debug("Timeline queue status: {} pending items, {} items with retries, {} total processed, {} total failed",
                    queueSize, activeRetries, totalProcessed.get(), totalFailed.get());
        }

        // Log warning if queue is getting large
        if (queueSize > 100) {
            log.warn("Timeline update queue is getting large: {} items pending. Consider scaling background processing.", queueSize);
        }
    }

    /**
     * Get queue statistics for monitoring.
     */
    public QueueStatistics getQueueStatistics() {
        return new QueueStatistics(
                timelineUpdateQueue.size(),
                retryCounters.size(),
                totalProcessed.get(),
                totalFailed.get()
        );
    }

    public int getQueueSize() {
        return timelineUpdateQueue.size();
    }

    public void clearQueue() {
        int cleared = timelineUpdateQueue.size();
        timelineUpdateQueue.clear();
        retryCounters.clear();
        log.info("Cleared {} items from timeline update queue and reset retry counters", cleared);
    }

    /**
     * Reset statistics counters.
     */
    public void resetStatistics() {
        totalProcessed.set(0);
        totalFailed.set(0);
        log.info("Reset timeline processing statistics");
    }

    /**
     * Queued timeline update with retry tracking.
     */
    public static class QueuedTimelineUpdate {
        private final TimelineKey timelineKey;
        private final Instant queuedAt;
        private final int retryCount;

        public QueuedTimelineUpdate(TimelineKey timelineKey, Instant queuedAt, int retryCount) {
            this.timelineKey = timelineKey;
            this.queuedAt = queuedAt;
            this.retryCount = retryCount;
        }

        public TimelineKey getTimelineKey() {
            return timelineKey;
        }

        public Instant getQueuedAt() {
            return queuedAt;
        }

        public int getRetryCount() {
            return retryCount;
        }

        @Override
        public String toString() {
            return "QueuedTimelineUpdate{" +
                    "timelineKey=" + timelineKey +
                    ", queuedAt=" + queuedAt +
                    ", retryCount=" + retryCount +
                    '}';
        }
    }

    /**
     * Queue statistics for monitoring.
     */
    public static class QueueStatistics {
        private final int queueSize;
        private final int activeRetries;
        private final int totalProcessed;
        private final int totalFailed;

        public QueueStatistics(int queueSize, int activeRetries, int totalProcessed, int totalFailed) {
            this.queueSize = queueSize;
            this.activeRetries = activeRetries;
            this.totalProcessed = totalProcessed;
            this.totalFailed = totalFailed;
        }

        public int getQueueSize() {
            return queueSize;
        }

        public int getActiveRetries() {
            return activeRetries;
        }

        public int getTotalProcessed() {
            return totalProcessed;
        }

        public int getTotalFailed() {
            return totalFailed;
        }

        @Override
        public String toString() {
            return "QueueStatistics{" +
                    "queueSize=" + queueSize +
                    ", activeRetries=" + activeRetries +
                    ", totalProcessed=" + totalProcessed +
                    ", totalFailed=" + totalFailed +
                    '}';
        }
    }

    /**
     * Timeline key for grouping updates by user and date
     */
    public static class TimelineKey {
        private final UUID userId;
        private final Instant date;

        public TimelineKey(UUID userId, Instant date) {
            this.userId = userId;
            this.date = date;
        }

        public UUID getUserId() {
            return userId;
        }

        public Instant getDate() {
            return date;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TimelineKey that = (TimelineKey) o;
            return userId.equals(that.userId) && date.equals(that.date);
        }

        @Override
        public int hashCode() {
            return userId.hashCode() * 31 + date.hashCode();
        }

        @Override
        public String toString() {
            return "TimelineKey{userId=" + userId + ", date=" + date + "}";
        }
    }
}