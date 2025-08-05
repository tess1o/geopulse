package org.github.tess1o.geopulse.timeline.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.timeline.events.FavoriteAddedEvent;
import org.github.tess1o.geopulse.timeline.events.FavoriteDeletedEvent;
import org.github.tess1o.geopulse.timeline.events.FavoriteRenamedEvent;
import org.github.tess1o.geopulse.timeline.model.ImpactAnalysis;
import org.github.tess1o.geopulse.timeline.model.TimelineStayEntity;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Analyzes the impact of favorite location changes on timeline data.
 * Determines whether changes can be handled with simple updates or require regeneration.
 */
@ApplicationScoped
@Slf4j
public class FavoriteImpactAnalyzer {

    @Inject
    FavoritesRepository favoritesRepository;

    @Inject
    TimelineStayRepository timelineStayRepository;

    // Distance thresholds for impact analysis
    private static final double MERGE_ANALYSIS_DISTANCE_METERS = 200.0;

    /**
     * Analyze the impact of adding a new favorite location.
     */
    public ImpactAnalysis analyzeAddition(FavoriteAddedEvent event) {
        log.debug("Analyzing impact of favorite addition: {} ({})", 
                 event.getFavoriteName(), event.getFavoriteType());

        // Find affected timeline stays
        List<LocalDate> affectedDates = findAffectedDatesForGeometry(
            event.getUserId(), event.getGeometry());

        if (affectedDates.isEmpty()) {
            return ImpactAnalysis.nameOnly(event.getFavoriteId(), 
                "No existing timeline stays affected by new favorite");
        }

        // Adding a favorite always requires regeneration because:
        // 1. It may cause existing stays to merge
        // 2. It changes location resolution for future requests
        return ImpactAnalysis.structural(event.getFavoriteId(), affectedDates,
            String.format("New %s favorite affects %d dates", 
                         event.getFavoriteType(), affectedDates.size()));
    }

    /**
     * Analyze the impact of deleting a favorite location.
     */
    public ImpactAnalysis analyzeDeletion(FavoriteDeletedEvent event) {
        log.debug("Analyzing impact of favorite deletion: {}", event.getFavoriteName());

        // Find timeline stays that reference this favorite
        List<TimelineStayEntity> affectedStays = timelineStayRepository.findByFavoriteId(event.getFavoriteId());
        
        if (affectedStays.isEmpty()) {
            return ImpactAnalysis.nameOnly(event.getFavoriteId(), 
                "No timeline stays reference deleted favorite");
        }

        List<LocalDate> affectedDates = affectedStays.stream()
            .map(stay -> stay.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate())
            .distinct()
            .sorted()
            .toList();

        // Deletion always requires regeneration because:
        // 1. Merged stays may need to be split back into individual stays
        // 2. Location names need to fall back to geocoding
        return ImpactAnalysis.structural(event.getFavoriteId(), affectedDates,
            String.format("Favorite deletion affects %d stays across %d dates", 
                         affectedStays.size(), affectedDates.size()));
    }

    /**
     * Analyze the impact of renaming a favorite location.
     */
    public ImpactAnalysis analyzeRename(FavoriteRenamedEvent event) {
        log.debug("Analyzing impact of favorite rename: '{}' -> '{}'", 
                 event.getOldName(), event.getNewName());

        // Get the favorite entity to check merge impact
        FavoritesEntity favorite = favoritesRepository.findById(event.getFavoriteId());
        if (favorite == null) {
            log.warn("Favorite not found for rename analysis: {}", event.getFavoriteId());
            return ImpactAnalysis.nameOnly(event.getFavoriteId(), "Favorite not found");
        }

        // Check if this favorite has merge impact
        if (!favorite.getMergeImpact()) {
            // Simple point favorite with no merge implications - can use fast path
            return ImpactAnalysis.nameOnly(event.getFavoriteId(),
                "Point favorite with no merge impact - safe for direct update");
        }

        // Find affected timeline stays
        List<TimelineStayEntity> affectedStays = timelineStayRepository.findByFavoriteId(event.getFavoriteId());
        
        if (affectedStays.isEmpty()) {
            return ImpactAnalysis.nameOnly(event.getFavoriteId(), 
                "No timeline stays reference renamed favorite");
        }

        List<LocalDate> affectedDates = affectedStays.stream()
            .map(stay -> stay.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate())
            .distinct()
            .sorted()
            .toList();

        // Complex favorite (area or with merge impact) requires regeneration
        return ImpactAnalysis.structural(event.getFavoriteId(), affectedDates,
            String.format("Complex favorite rename affects %d stays across %d dates", 
                         affectedStays.size(), affectedDates.size()));
    }

    /**
     * Pre-compute merge impact when a favorite is created.
     * This helps with future impact analysis.
     */
    public boolean computeMergeImpact(FavoritesEntity favorite) {
        // Area favorites always have merge impact
        if (favorite.getType() == FavoriteLocationType.AREA) {
            log.debug("Area favorite {} has merge impact", favorite.getName());
            return true;
        }

        // For simplicity, assume point favorites have no merge impact unless manually flagged
        // This can be enhanced later with proper spatial queries
        log.debug("Point favorite {} assumed to have no merge impact", favorite.getName());
        return false;
    }

    /**
     * Find dates that have timeline stays affected by a geometry.
     */
    private List<LocalDate> findAffectedDatesForGeometry(java.util.UUID userId, 
                                                         org.locationtech.jts.geom.Geometry geometry) {
        // Use appropriate distance based on geometry type
        double searchDistance = geometry.getGeometryType().equals("Point") ? 75.0 : 15.0;
        
        List<TimelineStayEntity> affectedStays = timelineStayRepository.findWithinDistance(
            userId, geometry, searchDistance);

        return affectedStays.stream()
            .map(stay -> stay.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate())
            .distinct()
            .sorted()
            .toList();
    }
}