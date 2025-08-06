package org.github.tess1o.geopulse.timeline.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.timeline.assembly.TimelineService;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineRegenerationTask;
import org.github.tess1o.geopulse.timeline.model.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineTripDTO;
import org.github.tess1o.geopulse.timeline.repository.TimelineRegenerationTaskRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background service for processing timeline regeneration tasks with priority handling.
 * 
 * Priority system:
 * - High priority (1): Favorite changes - processed immediately
 * - Low priority (2): Bulk imports - processed in batches during low activity
 */
@ApplicationScoped
@Slf4j
public class TimelineBackgroundService {

    @Inject
    TimelineRegenerationTaskRepository taskRepository;

    @Inject
    TimelineService timelineGenerationService;

    @Inject
    TimelineCacheService timelineCacheService;

    private final AtomicBoolean processing = new AtomicBoolean(false);

    // Configuration
    private static final int HIGH_PRIORITY_BATCH_SIZE = 5;
    private static final int LOW_PRIORITY_BATCH_SIZE = 10;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MINUTES = 5;

    /**
     * Process high-priority tasks immediately (every 2 seconds).
     * These are favorite changes that users expect to see quickly.
     */
    @Scheduled(every = "2s")
    public void processHighPriorityTasks() {
        if (processing.get()) {
            return;
        }

        List<TimelineRegenerationTask> highPriorityTasks = taskRepository
            .findHighPriorityPendingTasks(HIGH_PRIORITY_BATCH_SIZE);

        if (!highPriorityTasks.isEmpty()) {
            log.debug("Processing {} high-priority timeline regeneration tasks", highPriorityTasks.size());
            processTasks(highPriorityTasks, "HIGH_PRIORITY");
        }
    }

    /**
     * Process low-priority tasks during low activity (every 30 seconds).
     * These are bulk imports that can wait for system resources.
     */
    @Scheduled(every = "5s")
    public void processLowPriorityTasks() {
        if (processing.get()) {
            return;
        }

        // Only process low priority if no high priority tasks are waiting
        long highPriorityCount = taskRepository.countPendingByPriority(TimelineRegenerationTask.Priority.HIGH);
        if (highPriorityCount > 0) {
            log.debug("Skipping low-priority processing - {} high-priority tasks waiting", highPriorityCount);
            return;
        }

        List<TimelineRegenerationTask> lowPriorityTasks = taskRepository
            .findPendingTasks(LOW_PRIORITY_BATCH_SIZE);

        // Filter to only low priority tasks
        lowPriorityTasks = lowPriorityTasks.stream()
            .filter(task -> task.getPriority().equals(TimelineRegenerationTask.Priority.LOW.getValue()))
            .toList();

        if (!lowPriorityTasks.isEmpty()) {
            log.debug("Processing {} low-priority timeline regeneration tasks", lowPriorityTasks.size());
            processTasks(lowPriorityTasks, "LOW_PRIORITY");
        }
    }

    /**
     * Cleanup completed tasks (every hour).
     */
    @Scheduled(every = "1h")
    @Transactional
    public void cleanupCompletedTasks() {
        try {
            long deletedTasks = taskRepository.deleteCompletedTasksOlderThan(7); // Keep for 7 days
            if (deletedTasks > 0) {
                log.info("Cleaned up {} completed timeline regeneration tasks", deletedTasks);
            }
        } catch (Exception e) {
            log.error("Failed to cleanup completed tasks", e);
        }
    }

    /**
     * Process a batch of timeline regeneration tasks.
     */
    private void processTasks(List<TimelineRegenerationTask> tasks, String batchType) {
        if (!processing.compareAndSet(false, true)) {
            return; // Another process is already running
        }

        try {
            int successful = 0;
            int failed = 0;

            for (TimelineRegenerationTask task : tasks) {
                try {
                    if (processTask(task)) {
                        successful++;
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    log.error("Unexpected error processing task {}", task.getId(), e);
                    failed++;
                }
            }

            log.info("{} batch completed: {} successful, {} failed", batchType, successful, failed);

        } finally {
            processing.set(false);
        }
    }

    /**
     * Process a single timeline regeneration task using bulk generation.
     */
    @Transactional
    public boolean processTask(TimelineRegenerationTask task) {
        log.debug("Processing timeline regeneration task {} for user {} on dates {}-{} (attempt {})",
                 task.getId(), task.getUser().getId(), task.getStartDate(), task.getEndDate(), 
                 task.getRetryCount() + 1);

        // Check if task should be retried (delay between retries)
        if (task.getRetryCount() > 0 && task.getProcessingStartedAt() != null) {
            long minutesSinceLastAttempt = java.time.Duration.between(
                task.getProcessingStartedAt(), Instant.now()).toMinutes();
            
            if (minutesSinceLastAttempt < RETRY_DELAY_MINUTES) {
                log.debug("Task {} not ready for retry yet ({}min < {}min)", 
                         task.getId(), minutesSinceLastAttempt, RETRY_DELAY_MINUTES);
                return false;
            }
        }

        // Mark task as processing - need to reload to avoid detached entity issue
        TimelineRegenerationTask managedTask = taskRepository.findById(task.getId());
        if (managedTask == null) {
            log.warn("Task {} not found when attempting to process", task.getId());
            return false;
        }
        managedTask.setStatus(TimelineRegenerationTask.TaskStatus.PROCESSING);
        managedTask.setProcessingStartedAt(Instant.now());

        try {
            // Use bulk generation instead of day-by-day processing
            Instant rangeStart = managedTask.getStartDate().atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant rangeEnd = managedTask.getEndDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            
            log.info("Generating bulk timeline for user {} from {} to {} ({} days)", 
                    managedTask.getUser().getId(), managedTask.getStartDate(), managedTask.getEndDate(),
                    java.time.temporal.ChronoUnit.DAYS.between(managedTask.getStartDate(), managedTask.getEndDate()) + 1);

            // Generate timeline for entire range in one operation
            MovementTimelineDTO timeline = timelineGenerationService.getMovementTimeline(
                managedTask.getUser().getId(), rangeStart, rangeEnd);

            if (timeline != null && (!timeline.getStays().isEmpty() || !timeline.getTrips().isEmpty())) {
                // Save bulk timeline data using optimized batch save
                saveBulkTimeline(managedTask.getUser().getId(), timeline, managedTask.getStartDate(), managedTask.getEndDate());
                
                log.info("Successfully generated bulk timeline for user {} - {} stays, {} trips", 
                         managedTask.getUser().getId(), timeline.getStaysCount(), timeline.getTripsCount());
            } else {
                log.debug("No timeline data generated for date range {} to {} (no GPS data)", 
                         managedTask.getStartDate(), managedTask.getEndDate());
            }

            // Mark task as completed
            managedTask.setStatus(TimelineRegenerationTask.TaskStatus.COMPLETED);
            managedTask.setCompletedAt(Instant.now());
            managedTask.setErrorMessage(null);

            log.info("Successfully completed timeline regeneration task {} in bulk mode", 
                     managedTask.getId());
            return true;

        } catch (Exception e) {
            log.error("Failed to process timeline regeneration task {}: {}", managedTask.getId(), e.getMessage(), e);
            
            // Handle retry logic
            if (managedTask.getRetryCount() < MAX_RETRIES) {
                managedTask.setStatus(TimelineRegenerationTask.TaskStatus.PENDING);
                managedTask.setRetryCount(managedTask.getRetryCount() + 1);
                managedTask.setErrorMessage(e.getMessage());
                managedTask.setProcessingStartedAt(null); // Reset for retry delay calculation
                
                log.debug("Queued task {} for retry (attempt {} of {})", 
                         managedTask.getId(), managedTask.getRetryCount(), MAX_RETRIES + 1);
            } else {
                managedTask.setStatus(TimelineRegenerationTask.TaskStatus.FAILED);
                managedTask.setErrorMessage(e.getMessage());
                managedTask.setCompletedAt(Instant.now());
                
                log.error("Timeline regeneration task {} failed permanently after {} attempts", 
                         managedTask.getId(), MAX_RETRIES + 1);
            }
            
            return false;
        }
    }

    /**
     * Save bulk timeline data efficiently by grouping data by date.
     * This avoids individual save operations for each day.
     */
    private void saveBulkTimeline(UUID userId, MovementTimelineDTO timeline, LocalDate startDate, LocalDate endDate) {
        log.debug("Saving bulk timeline data for user {} ({} stays, {} trips)", 
                 userId, timeline.getStaysCount(), timeline.getTripsCount());
        
        // Group timeline data by date and save efficiently
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            Instant dayStart = currentDate.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant dayEnd = currentDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            
            // Filter timeline data for this specific date
            MovementTimelineDTO dailyTimeline = filterTimelineByDateRange(timeline, dayStart, dayEnd);
            
            if (dailyTimeline != null && (!dailyTimeline.getStays().isEmpty() || !dailyTimeline.getTrips().isEmpty())) {
                timelineCacheService.save(userId, dayStart, dayEnd, dailyTimeline);
                log.debug("Saved timeline data for {} - {} stays, {} trips", 
                         currentDate, dailyTimeline.getStaysCount(), dailyTimeline.getTripsCount());
            }
            
            currentDate = currentDate.plusDays(1);
        }
    }
    
    /**
     * Filter timeline data to only include events within the specified date range.
     */
    private MovementTimelineDTO filterTimelineByDateRange(MovementTimelineDTO timeline, Instant startTime, Instant endTime) {
        // Create a new timeline DTO with only the data from the specified date range
        List<TimelineStayLocationDTO> filteredStays = timeline.getStays().stream()
            .filter(stay -> stay.getTimestamp().compareTo(startTime) >= 0 && stay.getTimestamp().compareTo(endTime) < 0)
            .toList();
            
        List<TimelineTripDTO> filteredTrips = timeline.getTrips().stream()
            .filter(trip -> trip.getTimestamp().compareTo(startTime) >= 0 && trip.getTimestamp().compareTo(endTime) < 0)
            .toList();
        
        if (filteredStays.isEmpty() && filteredTrips.isEmpty()) {
            return null;
        }
        
        MovementTimelineDTO filteredTimeline = new MovementTimelineDTO(timeline.getUserId(), filteredStays, filteredTrips);
        filteredTimeline.setDataSource(timeline.getDataSource());
        filteredTimeline.setLastUpdated(timeline.getLastUpdated());
        return filteredTimeline;
    }

    /**
     * Queue a high-priority regeneration task (for favorite changes).
     */
    @Transactional
    public void queueHighPriorityRegeneration(java.util.UUID userId, List<LocalDate> dates) {
        if (dates.isEmpty()) {
            return;
        }

        LocalDate startDate = dates.stream().min(LocalDate::compareTo).orElse(LocalDate.now());
        LocalDate endDate = dates.stream().max(LocalDate::compareTo).orElse(LocalDate.now());

        // Check if task already exists
        if (taskRepository.existsPendingTask(userId, startDate, endDate, 
                                           TimelineRegenerationTask.Priority.HIGH.getValue())) {
            log.debug("High-priority regeneration task already exists for user {} dates {}-{}", 
                     userId, startDate, endDate);
            return;
        }

        TimelineRegenerationTask task = new TimelineRegenerationTask();
        task.setUser(new org.github.tess1o.geopulse.user.model.UserEntity());
        task.getUser().setId(userId);
        task.setStartDate(startDate);
        task.setEndDate(endDate);
        task.setPriority(TimelineRegenerationTask.Priority.HIGH.getValue());
        task.setStatus(TimelineRegenerationTask.TaskStatus.PENDING);

        taskRepository.persist(task);
        
        log.info("Queued high-priority timeline regeneration for user {} covering {} dates ({} to {})", 
                userId, dates.size(), startDate, endDate);
    }

    /**
     * Queue a low-priority regeneration task (for bulk imports).
     */
    @Transactional
    public void queueLowPriorityRegeneration(java.util.UUID userId, LocalDate startDate, LocalDate endDate) {
        // Check if task already exists
        if (taskRepository.existsPendingTask(userId, startDate, endDate, 
                                           TimelineRegenerationTask.Priority.LOW.getValue())) {
            log.debug("Low-priority regeneration task already exists for user {} dates {}-{}", 
                     userId, startDate, endDate);
            return;
        }

        TimelineRegenerationTask task = new TimelineRegenerationTask();
        task.setUser(new org.github.tess1o.geopulse.user.model.UserEntity());
        task.getUser().setId(userId);
        task.setStartDate(startDate);
        task.setEndDate(endDate);
        task.setPriority(TimelineRegenerationTask.Priority.LOW.getValue());
        task.setStatus(TimelineRegenerationTask.TaskStatus.PENDING);

        taskRepository.persist(task);
        
        log.info("Queued low-priority timeline regeneration for user {} from {} to {}", 
                userId, startDate, endDate);
    }

    /**
     * Get current queue status for monitoring.
     */
    public QueueStatus getQueueStatus() {
        long highPriorityPending = taskRepository.countPendingByPriority(TimelineRegenerationTask.Priority.HIGH);
        long lowPriorityPending = taskRepository.countPendingByPriority(TimelineRegenerationTask.Priority.LOW);
        
        return new QueueStatus(highPriorityPending, lowPriorityPending, processing.get());
    }

    /**
     * Queue status information.
     */
    public static class QueueStatus {
        public final long highPriorityPending;
        public final long lowPriorityPending;
        public final boolean processing;

        public QueueStatus(long highPriorityPending, long lowPriorityPending, boolean processing) {
            this.highPriorityPending = highPriorityPending;
            this.lowPriorityPending = lowPriorityPending;
            this.processing = processing;
        }
    }
}