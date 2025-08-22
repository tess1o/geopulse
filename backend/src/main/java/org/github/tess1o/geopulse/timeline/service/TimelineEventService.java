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
import org.github.tess1o.geopulse.timeline.service.redesign.TimelineRequestRouter;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;

import java.time.Instant;
import java.time.ZoneOffset;

import java.util.List;

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

    @Inject
    TimelineRequestRouter timelineRequestRouter;

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
            // First get cached dates before deleting them
            List<java.time.LocalDate> cachedDates = timelineCacheService.getCachedDates(event.getUserId());
            
            // Delete all cached timeline data for the user
            timelineCacheService.deleteAll(event.getUserId());
            
            if (!cachedDates.isEmpty()) {
                // Use direct synchronous generation like UI regenerate endpoint (fast path)
                java.time.LocalDate firstDate = cachedDates.get(0);
                java.time.LocalDate lastDate = cachedDates.get(cachedDates.size() - 1);
                
                // Generate timeline for entire range in one operation (like UI /regenerate endpoint)
                Instant rangeStart = firstDate.atStartOfDay(ZoneOffset.UTC).toInstant();
                Instant rangeEnd = lastDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                
                long startTime = System.currentTimeMillis();
                log.info("Generating direct timeline for user {} from {} to {} ({} days) - preferences update", 
                        event.getUserId(), firstDate, lastDate,
                        java.time.temporal.ChronoUnit.DAYS.between(firstDate, lastDate) + 1);

                MovementTimelineDTO timeline = timelineRequestRouter.getTimeline(
                    event.getUserId(), rangeStart, rangeEnd);

                if (timeline != null && (!timeline.getStays().isEmpty() || !timeline.getTrips().isEmpty())) {
                    // Save timeline data using bulk approach like background service
                    saveBulkTimelineData(event.getUserId(), timeline, firstDate, lastDate);
                    
                    log.info("Successfully regenerated timeline for user {} - {} stays, {} trips in {} s", 
                             event.getUserId(), timeline.getStaysCount(), timeline.getTripsCount(),
                             (System.currentTimeMillis() - startTime) / 1000.0f);
                } else {
                    log.info("No timeline data generated for user {} (no GPS data in range)", 
                            event.getUserId());
                }
                
                log.info("Completed direct timeline regeneration for user {} covering {} dates from {} to {}", 
                        event.getUserId(), cachedDates.size(), firstDate, lastDate);
            } else {
                log.info("No cached timeline data found for user {} - nothing to regenerate", 
                        event.getUserId());
            }
            
        } catch (Exception e) {
            log.error("Failed to process timeline preferences updated event for user {}: {}", 
                     event.getUserId(), e.getMessage(), e);
        }
    }
    
    /**
     * Split large date ranges into smaller chunks for more manageable background processing.
     * This prevents creating massive tasks that could cause performance issues.
     */
    private void splitAndQueueRegeneration(java.util.UUID userId, java.time.LocalDate startDate, java.time.LocalDate endDate) {
        final int MAX_DAYS_PER_TASK = 30; // Split into monthly chunks
        
        java.time.LocalDate currentStart = startDate;
        while (currentStart.isBefore(endDate) || currentStart.equals(endDate)) {
            java.time.LocalDate currentEnd = currentStart.plusDays(MAX_DAYS_PER_TASK - 1);
            if (currentEnd.isAfter(endDate)) {
                currentEnd = endDate;
            }
            
            backgroundService.queueLowPriorityRegeneration(userId, currentStart, currentEnd);
            log.debug("Queued regeneration for user {} from {} to {} ({} days)", 
                     userId, currentStart, currentEnd, 
                     java.time.temporal.ChronoUnit.DAYS.between(currentStart, currentEnd) + 1);
            
            currentStart = currentEnd.plusDays(1);
        }
    }

    /**
     * Handle simple name updates with direct SQL execution (fast path).
     */
    private void handleSimpleNameUpdate(FavoriteRenamedEvent event) {
        int updatedStays = entityManager.createQuery(
            "UPDATE TimelineStayEntity s SET s.locationName = :newName " +
            "WHERE s.favoriteLocation.id = :favoriteId")
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

    /**
     * Save bulk timeline data efficiently by grouping data by date.
     * This mirrors the approach used in TimelineBackgroundService for consistency.
     */
    private void saveBulkTimelineData(java.util.UUID userId, MovementTimelineDTO timeline, 
                                     java.time.LocalDate startDate, java.time.LocalDate lastDate) {
        log.debug("Saving bulk timeline data for user {} ({} stays, {} trips)", 
                 userId, timeline.getStaysCount(), timeline.getTripsCount());
        
        // Group timeline data by date and save efficiently
        java.time.LocalDate currentDate = startDate;
        while (!currentDate.isAfter(lastDate)) {
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
     * This mirrors the approach used in TimelineBackgroundService for consistency.
     */
    private MovementTimelineDTO filterTimelineByDateRange(MovementTimelineDTO timeline, Instant startTime, Instant endTime) {
        // Create a new timeline DTO with only the data from the specified date range
        List<org.github.tess1o.geopulse.timeline.model.TimelineStayLocationDTO> filteredStays = timeline.getStays().stream()
            .filter(stay -> stay.getTimestamp().compareTo(startTime) >= 0 && stay.getTimestamp().compareTo(endTime) < 0)
            .toList();
            
        List<org.github.tess1o.geopulse.timeline.model.TimelineTripDTO> filteredTrips = timeline.getTrips().stream()
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
}