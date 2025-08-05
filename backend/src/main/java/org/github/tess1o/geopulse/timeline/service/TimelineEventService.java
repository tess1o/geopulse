package org.github.tess1o.geopulse.timeline.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.timeline.events.FavoriteAddedEvent;
import org.github.tess1o.geopulse.timeline.events.FavoriteDeletedEvent;
import org.github.tess1o.geopulse.timeline.events.FavoriteRenamedEvent;
import org.github.tess1o.geopulse.timeline.events.TimelinePreferencesUpdatedEvent;
import org.github.tess1o.geopulse.timeline.model.ImpactAnalysis;

/**
 * Simplified timeline event service using the new impact analysis system.
 * 
 * Handles favorite changes with two strategies:
 * 1. Fast path: Direct SQL updates for simple name changes
 * 2. Slow path: Delete cache + priority queue regeneration for structural changes
 */
@ApplicationScoped
@Slf4j
public class TimelineEventService {

    @Inject
    FavoriteImpactAnalyzer impactAnalyzer;

    @Inject
    TimelineCacheService timelineCacheService;

    @Inject
    TimelineBackgroundService backgroundService;

    @Inject
    EntityManager entityManager;

    /**
     * Handle favorite addition events.
     */
    @Transactional
    public void onFavoriteAdded(@Observes FavoriteAddedEvent event) {
        log.info("Processing favorite added event: {} for user {}", event.getFavoriteName(), event.getUserId());
        
        try {
            ImpactAnalysis analysis = impactAnalyzer.analyzeAddition(event);
            
            if (analysis.isSimpleUpdate()) {
                log.debug("Favorite addition has no impact on existing timeline data");
                return;
            }
            
            // Structural change - delete cache and queue regeneration
            handleStructuralChange(event.getUserId(), analysis);
            
        } catch (Exception e) {
            log.error("Failed to process favorite added event for user {}: {}", 
                     event.getUserId(), e.getMessage(), e);
        }
    }

    /**
     * Handle favorite deletion events.
     */
    @Transactional
    public void onFavoriteDeleted(@Observes FavoriteDeletedEvent event) {
        log.info("Processing favorite deleted event: {} for user {}", event.getFavoriteName(), event.getUserId());
        
        try {
            ImpactAnalysis analysis = impactAnalyzer.analyzeDeletion(event);
            
            if (analysis.isSimpleUpdate()) {
                log.debug("Favorite deletion has no impact on timeline data");
                return;
            }
            
            // Deletion always requires regeneration due to potential unmerging
            handleStructuralChange(event.getUserId(), analysis);
            
        } catch (Exception e) {
            log.error("Failed to process favorite deleted event for user {}: {}", 
                     event.getUserId(), e.getMessage(), e);
        }
    }

    /**
     * Handle favorite rename events.
     */
    @Transactional
    public void onFavoriteRenamed(@Observes FavoriteRenamedEvent event) {
        log.info("Processing favorite renamed event: '{}' -> '{}' for user {}", 
                 event.getOldName(), event.getNewName(), event.getUserId());
        
        try {
            ImpactAnalysis analysis = impactAnalyzer.analyzeRename(event);
            
            if (analysis.isSimpleUpdate()) {
                // Fast path: Direct SQL update
                handleSimpleNameUpdate(event);
            } else {
                // Slow path: Delete cache and queue regeneration
                handleStructuralChange(event.getUserId(), analysis);
            }
            
        } catch (Exception e) {
            log.error("Failed to process favorite renamed event for user {}: {}", 
                     event.getUserId(), e.getMessage(), e);
        }
    }

    /**
     * Handle timeline preferences update events.
     * When preferences change, ALL timeline data for the user needs to be regenerated.
     */
    @Transactional
    public void onTimelinePreferencesUpdated(@Observes TimelinePreferencesUpdatedEvent event) {
        log.info("Processing timeline preferences updated event for user {} (resetToDefaults={})", 
                 event.getUserId(), event.isWasResetToDefaults());
        
        try {
            // Delete all cached timeline data for the user
            timelineCacheService.deleteAll(event.getUserId());
            
            // Get all dates that had timeline data
            // Note: Since we just deleted the cache, we need to infer date ranges from GPS data
            // For now, queue a broad regeneration - this could be optimized later
            java.time.LocalDate startDate = java.time.LocalDate.now().minusYears(1); // Last year
            java.time.LocalDate endDate = java.time.LocalDate.now().minusDays(1); // Up to yesterday
            
            backgroundService.queueLowPriorityRegeneration(event.getUserId(), startDate, endDate);
            
            log.info("Invalidated all timeline data for user {} due to preference changes", event.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to process timeline preferences updated event for user {}: {}", 
                     event.getUserId(), e.getMessage(), e);
        }
    }

    /**
     * Handle simple name updates with direct SQL execution (fast path).
     */
    private void handleSimpleNameUpdate(FavoriteRenamedEvent event) {
        int updatedStays = entityManager.createQuery(
            "UPDATE TimelineStayEntity s SET s.locationName = :newName " +
            "WHERE s.favoriteId = :favoriteId")
            .setParameter("newName", event.getNewName())
            .setParameter("favoriteId", event.getFavoriteId())
            .executeUpdate();

        log.info("Updated {} timeline stays with new favorite name '{}' for favorite {}", 
                updatedStays, event.getNewName(), event.getFavoriteId());
        
        // Note: We could also update any cached aggregation data here if needed
    }

    /**
     * Handle structural changes that require cache deletion and regeneration (slow path).
     */
    private void handleStructuralChange(java.util.UUID userId, ImpactAnalysis analysis) {
        if (analysis.getAffectedDates().isEmpty()) {
            log.debug("No affected dates for structural change - no action needed");
            return;
        }

        // Delete cached timeline data for affected dates
        timelineCacheService.delete(userId, analysis.getAffectedDates());
        
        // Queue high-priority regeneration
        backgroundService.queueHighPriorityRegeneration(userId, analysis.getAffectedDates());
        
        log.info("Processed structural change affecting {} dates: {}", 
                analysis.getAffectedDates().size(), analysis.getReason());
    }
}