package org.github.tess1o.geopulse.timeline.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.timeline.model.TimelineRegenerationTask;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository for timeline regeneration task queue management.
 */
@ApplicationScoped
public class TimelineRegenerationTaskRepository implements PanacheRepositoryBase<TimelineRegenerationTask, UUID> {

    /**
     * Find pending tasks ordered by priority and creation time.
     */
    public List<TimelineRegenerationTask> findPendingTasks(int limit) {
        return find("status = ?1 ORDER BY priority ASC, createdAt ASC", 
                   TimelineRegenerationTask.TaskStatus.PENDING)
               .page(0, limit)
               .list();
    }

    /**
     * Find high priority pending tasks.
     */
    public List<TimelineRegenerationTask> findHighPriorityPendingTasks(int limit) {
        return find("status = ?1 AND priority = ?2 ORDER BY createdAt ASC", 
                   TimelineRegenerationTask.TaskStatus.PENDING,
                   TimelineRegenerationTask.Priority.HIGH.getValue())
               .page(0, limit)
               .list();
    }

    /**
     * Find tasks for a specific user and date range.
     */
    public List<TimelineRegenerationTask> findByUserAndDateRange(UUID userId, LocalDate startDate, LocalDate endDate) {
        return find("user.id = ?1 AND startDate <= ?3 AND endDate >= ?2", 
                   userId, startDate, endDate)
               .list();
    }

    /**
     * Check if a task already exists for the given parameters.
     */
    public boolean existsPendingTask(UUID userId, LocalDate startDate, LocalDate endDate, int priority) {
        return count("user.id = ?1 AND startDate = ?2 AND endDate = ?3 AND priority = ?4 AND status = ?5",
                    userId, startDate, endDate, priority, TimelineRegenerationTask.TaskStatus.PENDING) > 0;
    }

    /**
     * Count pending tasks by priority.
     */
    public long countPendingByPriority(TimelineRegenerationTask.Priority priority) {
        return count("status = ?1 AND priority = ?2", 
                    TimelineRegenerationTask.TaskStatus.PENDING, 
                    priority.getValue());
    }

    /**
     * Find failed tasks that can be retried.
     */
    public List<TimelineRegenerationTask> findRetryableTasks(int maxRetries, int limit) {
        return find("status = ?1 AND retryCount < ?2 ORDER BY createdAt ASC", 
                   TimelineRegenerationTask.TaskStatus.FAILED, maxRetries)
               .page(0, limit)
               .list();
    }

    /**
     * Clean up completed tasks older than specified days.
     */
    public long deleteCompletedTasksOlderThan(int days) {
        return delete("status = ?1 AND completedAt < (CURRENT_TIMESTAMP - ?2 DAY)", 
                     TimelineRegenerationTask.TaskStatus.COMPLETED, days);
    }

    /**
     * Delete all tasks for a specific user.
     * Used during test cleanup to avoid foreign key constraint violations.
     */
    public long deleteByUserId(UUID userId) {
        return delete("user.id = ?1", userId);
    }
}