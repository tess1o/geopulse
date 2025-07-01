package org.github.tess1o.geopulse.timeline.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.timeline.events.FavoriteAddedEvent;
import org.github.tess1o.geopulse.timeline.events.FavoriteDeletedEvent;
import org.github.tess1o.geopulse.timeline.events.FavoriteRenamedEvent;
import org.github.tess1o.geopulse.timeline.events.TimelinePreferencesUpdatedEvent;
import org.github.tess1o.geopulse.timeline.model.TimelineStayEntity;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;

import java.util.List;

@ApplicationScoped
@Slf4j
public class TimelineEventService {
    
    private final TimelineStayRepository stayRepository;
    private final TimelineInvalidationService timelineInvalidationService;
    private final FavoriteDeletionHandler favoriteDeletionHandler;

    // Default proximity thresholds (can be made configurable later if needed)
    private static final double DEFAULT_POINT_PROXIMITY_METERS = 75.0;
    private static final double DEFAULT_AREA_PROXIMITY_METERS = 15.0;
    
    public TimelineEventService(TimelineStayRepository stayRepository,
                                TimelineInvalidationService timelineInvalidationService,
                                FavoriteDeletionHandler favoriteDeletionHandler) {
        this.stayRepository = stayRepository;
        this.timelineInvalidationService = timelineInvalidationService;
        this.favoriteDeletionHandler = favoriteDeletionHandler;
    }
    
    public void onFavoriteAdded(@Observes FavoriteAddedEvent event) {
        log.info("Processing favorite added event: {} for user {}", event.getFavoriteName(), event.getUserId());
        
        try {
            // Find all timelines that could be affected
            List<TimelineStayEntity> affectedStays = findAffectedStays(event);
            
            if (affectedStays.isEmpty()) {
                log.debug("No timeline stays affected by new favorite: {}", event.getFavoriteName());
                return;
            }
            
            log.info("Found {} timeline stays potentially affected by new favorite: {}", 
                     affectedStays.size(), event.getFavoriteName());
            
            // Mark as stale and queue for update
            timelineInvalidationService.markStaleAndQueue(affectedStays);
            
        } catch (Exception e) {
            log.error("Failed to process favorite added event for user {}: {}", 
                     event.getUserId(), e.getMessage(), e);
        }
    }
    
    @Transactional
    public void onFavoriteDeleted(@Observes FavoriteDeletedEvent event) {
        log.info("Processing favorite deleted event: {} for user {}", event.getFavoriteName(), event.getUserId());
        
        try {
            // Find all timeline stays that reference the deleted favorite
            List<TimelineStayEntity> affectedStays = stayRepository.findByFavoriteId(event.getFavoriteId());
            
            if (affectedStays.isEmpty()) {
                log.debug("No timeline stays affected by favorite deletion: {}", event.getFavoriteName());
                return;
            }
            
            log.info("Processing {} timeline stays affected by favorite deletion: {}", 
                     affectedStays.size(), event.getFavoriteName());
            
            // Apply deletion strategy - use revert to geocoding as default
            // TODO: Add user preference for deletion strategy in timeline config
            FavoriteDeletionStrategy strategy = FavoriteDeletionStrategy.REVERT_TO_GEOCODING;
            
            favoriteDeletionHandler.handleDeletion(affectedStays, strategy);
            
        } catch (Exception e) {
            log.error("Failed to process favorite deleted event for user {}: {}", 
                     event.getUserId(), e.getMessage(), e);
        }
    }
    
    public void onFavoriteRenamed(@Observes FavoriteRenamedEvent event) {
        log.info("Processing favorite renamed event: '{}' -> '{}' for user {}", 
                 event.getOldName(), event.getNewName(), event.getUserId());
        
        try {
            // Get user's timeline configuration to check preferences
            // TODO: Add user preference for updating historical names in timeline config
            // For now, use default behavior of updating historical names
            boolean shouldUpdateHistoricalNames = true;
            
            if (shouldUpdateHistoricalNames) {
                List<TimelineStayEntity> affectedStays = stayRepository.findByFavoriteId(event.getFavoriteId());
                
                if (affectedStays.isEmpty()) {
                    log.debug("No timeline stays affected by favorite rename: {} -> {}", 
                             event.getOldName(), event.getNewName());
                    return;
                }
                
                log.info("Found {} timeline stays to update for favorite rename: {} -> {}", 
                         affectedStays.size(), event.getOldName(), event.getNewName());
                
                timelineInvalidationService.markStaleAndQueue(affectedStays);
            } else {
                log.debug("Skipping historical name updates for favorite rename based on user preference");
            }
            
        } catch (Exception e) {
            log.error("Failed to process favorite renamed event for user {}: {}", 
                     event.getUserId(), e.getMessage(), e);
        }
    }
    
    private List<TimelineStayEntity> findAffectedStays(FavoriteAddedEvent event) {
        if (event.getFavoriteType() == FavoriteLocationType.POINT) {
            // Find stays within proximity of favorite point
            return stayRepository.findWithinDistance(
                event.getUserId(), 
                event.getGeometry(), 
                DEFAULT_POINT_PROXIMITY_METERS
            );
        } else {
            // Find stays within or near favorite area
            return stayRepository.findWithinOrNearArea(
                event.getUserId(),
                event.getGeometry(),
                DEFAULT_AREA_PROXIMITY_METERS
            );
        }
    }
    
    /**
     * Handle timeline preferences update events.
     * When preferences change, ALL timeline data for the user needs to be regenerated
     * since preferences affect timeline generation algorithms globally.
     */
    @Transactional
    public void onTimelinePreferencesUpdated(@Observes TimelinePreferencesUpdatedEvent event) {
        log.info("Processing timeline preferences updated event for user {} (resetToDefaults={})", 
                 event.getUserId(), event.isWasResetToDefaults());
        
        try {
            // Find ALL timeline stays for the user - preferences affect everything
            List<TimelineStayEntity> allStays = stayRepository.findByUser(event.getUserId());
            
            if (allStays.isEmpty()) {
                log.debug("No timeline stays found for user {} - nothing to invalidate", event.getUserId());
                return;
            }
            
            log.info("Found {} timeline stays for user {} - marking all as stale due to preference changes", 
                     allStays.size(), event.getUserId());
            
            // Mark ALL stays as stale and queue for regeneration
            timelineInvalidationService.markStaleAndQueue(allStays);
            
            log.info("Successfully invalidated {} timeline stays for user {} due to preference changes", 
                     allStays.size(), event.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to process timeline preferences updated event for user {}: {}", 
                     event.getUserId(), e.getMessage(), e);
        }
    }
}