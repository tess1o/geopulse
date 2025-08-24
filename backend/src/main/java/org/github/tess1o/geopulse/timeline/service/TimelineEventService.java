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
 * <p>
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
            regenerateFullTimelineForUser(event.getUserId(), "preferences update");
        } catch (Exception e) {
            log.error("Failed to process timeline preferences updated event for user {}: {}",
                    event.getUserId(), e.getMessage(), e);
        }
    }

    /**
     * Regenerate the complete timeline for a user by deleting all cached data and regenerating
     * from scratch. This is a comprehensive operation that covers all dates where the user
     * previously had timeline data.
     *
     * @param userId The user ID to regenerate timeline for
     * @param reason A descriptive reason for the regeneration (for logging)
     * @return true if regeneration was successful or no data needed regeneration, false if failed
     */
    @Transactional
    public boolean regenerateFullTimelineForUser(java.util.UUID userId, String reason) {
        log.info("Starting full timeline regeneration for user {} - reason: {}", userId, reason);

        try {
            // First get cached dates before deleting them
            List<java.time.LocalDate> cachedDates = timelineCacheService.getCachedDates(userId);

            // Delete all cached timeline data for the user
            timelineCacheService.deleteAll(userId);

            if (!cachedDates.isEmpty()) {
                java.time.LocalDate firstDate = cachedDates.get(0);
                java.time.LocalDate lastDate = cachedDates.get(cachedDates.size() - 1);

                Instant rangeStart = firstDate.atStartOfDay(ZoneOffset.UTC).toInstant();
                Instant rangeEnd = lastDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

                long startTime = System.currentTimeMillis();
                log.info("Generating direct timeline for user {} from {} to {} ({} days) - {}",
                        userId, firstDate, lastDate,
                        java.time.temporal.ChronoUnit.DAYS.between(firstDate, lastDate) + 1, reason);

                // Let TimelineRequestRouter handle everything (including persistence)
                MovementTimelineDTO timeline = timelineRequestRouter.getTimeline(userId, rangeStart, rangeEnd);

                if (timeline != null && (!timeline.getStays().isEmpty() || !timeline.getTrips().isEmpty() || !timeline.getDataGaps().isEmpty())) {
                    log.info("Successfully regenerated timeline for user {} - {} stays, {} trips, {} data gaps in {} s",
                            userId, timeline.getStaysCount(), timeline.getTripsCount(), timeline.getDataGapsCount(),
                            (System.currentTimeMillis() - startTime) / 1000.0f);
                } else {
                    log.info("No timeline data generated for user {} (no GPS data in range)", userId);
                }

                log.info("Completed full timeline regeneration for user {} covering {} dates from {} to {}",
                        userId, cachedDates.size(), firstDate, lastDate);
                return true;
            } else {
                log.info("No cached timeline data found for user {} - nothing to regenerate", userId);
                return true; // Consider this successful since there was nothing to regenerate
            }

        } catch (Exception e) {
            log.error("Failed to regenerate full timeline for user {}: {}", userId, e.getMessage(), e);
            return false;
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

}