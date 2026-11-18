package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.events.FavoriteDeletedEvent;
import org.github.tess1o.geopulse.streaming.events.FavoriteRenamedEvent;
import org.github.tess1o.geopulse.streaming.events.TimelinePreferencesUpdatedEvent;
import org.github.tess1o.geopulse.streaming.events.TravelClassificationUpdatedEvent;
import org.github.tess1o.geopulse.streaming.events.TimelineStructureUpdatedEvent;
import org.github.tess1o.geopulse.streaming.service.trips.TripReclassificationService;


@ApplicationScoped
@Slf4j
public class StreamingTimelineEventsService {

    @Inject
    StreamingPersistenceManager materializer;

    
    @Inject
    TripReclassificationService tripReclassificationService;

    @Transactional(value = Transactional.TxType.MANDATORY)
    public void onFavoriteDeleted(@Observes(during = TransactionPhase.IN_PROGRESS) FavoriteDeletedEvent event) {
        log.info("Processing favorite deleted event: {} for user {}", event.getFavoriteName(), event.getUserId());

        materializer.invalidateFavoriteStays(event.getUserId(), event.getFavoriteId());

        // Timeline regeneration is now handled by FavoriteLocationService with async job
    }

    /**
     * Handle favorite rename events.
     */
    @Transactional
    public void onFavoriteRenamed(@Observes FavoriteRenamedEvent event) {
        log.info("Processing favorite renamed event: '{}' -> '{}' for user {}",
                event.getOldName(), event.getNewName(), event.getUserId());
        renameTimelineLocation(event);
    }

    @Transactional
    public void onTimelinePreferencesUpdated(@Observes TimelinePreferencesUpdatedEvent event) {
        log.info("Processing timeline preferences updated event for user {} (resetToDefaults={})",
                event.getUserId(), event.isWasResetToDefaults());

        // Timeline regeneration is now handled by UserService with async job
    }

    /**
     * Handle travel classification updated events - fast trip type recalculation.
     * This is much faster than full timeline regeneration and runs synchronously.
     */
    @Transactional
    public void onTravelClassificationUpdated(@Observes TravelClassificationUpdatedEvent event) {
        log.info("Processing travel classification updated event for user {} (resetToDefaults={})",
                event.getUserId(), event.isWasResetToDefaults());
        try {
            tripReclassificationService.reclassifyUserTrips(event.getUserId());
        } catch (Exception e) {
            log.error("Failed to process travel classification updated event for user {}: {}",
                    event.getUserId(), e.getMessage(), e);
        }
    }

    /**
     * Handle timeline structure updated events - full timeline regeneration.
     * Used when structural parameters (staypoint detection, merging, etc.) change.
     */
    @Transactional
    public void onTimelineStructureUpdated(@Observes TimelineStructureUpdatedEvent event) {
        log.info("Processing timeline structure updated event for user {} (resetToDefaults={})",
                event.getUserId(), event.isWasResetToDefaults());
        // Timeline regeneration is now handled by UserService with async job
    }

    private void renameTimelineLocation(FavoriteRenamedEvent event) {
        int updatedStays = materializer.updateFavoritePlaceName(event.getUserId(), event.getFavoriteId(), event.getNewName());

        log.info("Updated {} timeline stays with new favorite name '{}' for favorite {}",
                updatedStays, event.getNewName(), event.getFavoriteId());
    }
}
