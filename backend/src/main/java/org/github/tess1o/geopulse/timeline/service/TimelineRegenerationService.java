package org.github.tess1o.geopulse.timeline.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.service.LocationResolutionResult;
import org.github.tess1o.geopulse.timeline.assembly.TimelineDataService;
import org.github.tess1o.geopulse.timeline.assembly.TimelineService;
import org.github.tess1o.geopulse.timeline.mapper.TimelinePersistenceMapper;
import org.github.tess1o.geopulse.timeline.model.LocationSource;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineStayEntity;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.timeline.repository.TimelineTripRepository;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class TimelineRegenerationService {
    
    private final TimelineStayRepository stayRepository;
    private final TimelineTripRepository tripRepository;
    private final TimelineDataService timelineDataService;
    private final TimelineService timelineService;
    private final TimelineVersionService versionService;
    private final TimelinePersistenceService persistenceService;
    private final TimelinePersistenceMapper persistenceMapper;
    private final FavoritesRepository favoritesRepository;
    private final ReverseGeocodingLocationRepository geocodingRepository;
    
    public TimelineRegenerationService(TimelineStayRepository stayRepository,
                                       TimelineTripRepository tripRepository,
                                       TimelineDataService timelineDataService,
                                       TimelineService timelineService,
                                       TimelineVersionService versionService,
                                       TimelinePersistenceService persistenceService,
                                       TimelinePersistenceMapper persistenceMapper,
                                       FavoritesRepository favoritesRepository,
                                       ReverseGeocodingLocationRepository geocodingRepository) {
        this.stayRepository = stayRepository;
        this.tripRepository = tripRepository;
        this.timelineDataService = timelineDataService;
        this.timelineService = timelineService;
        this.versionService = versionService;
        this.persistenceService = persistenceService;
        this.persistenceMapper = persistenceMapper;
        this.favoritesRepository = favoritesRepository;
        this.geocodingRepository = geocodingRepository;
    }
    
    /**
     * Regenerate timeline for a specific user and date using the most efficient strategy.
     * 
     * @param userId user ID
     * @param date date to regenerate (truncated to day), or Instant.EPOCH for full user regeneration
     * @return regenerated timeline
     */
    @Transactional
    public MovementTimelineDTO regenerateTimeline(UUID userId, Instant date) {
        // Check if this is a full user regeneration (marked with Instant.EPOCH)
        if (date.equals(Instant.EPOCH)) {
            log.info("Starting FULL timeline regeneration for user {}", userId);
            return regenerateFullUserTimeline(userId);
        }
        
        log.info("Starting timeline regeneration for user {} on date {}", userId, date);
        
        LocalDate localDate = date.atZone(ZoneOffset.UTC).toLocalDate();
        Instant startOfDay = localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = localDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        
        // Get affected stays for the day
        List<TimelineStayEntity> staleStays = stayRepository.findStaleByUserAndDate(userId, date);
        
        if (staleStays.isEmpty()) {
            // No stale data, return cached timeline if available
            List<TimelineStayEntity> cachedStays = stayRepository.findByUserAndDate(userId, date);
            if (!cachedStays.isEmpty()) {
                log.debug("No stale data found, returning existing cached timeline");
                return buildTimelineFromEntities(cachedStays);
            }
        }
        
        // Determine regeneration strategy
        RegenerationStrategy strategy = determineOptimalStrategy(userId, staleStays, date);

        return switch (strategy) {
            case LOCATION_RESOLUTION_ONLY -> {
                log.debug("Using location resolution only strategy");
                yield updateLocationResolutionOnly(userId, staleStays, date);
            }
            case SELECTIVE_MERGE_UPDATE -> {
                log.debug("Using selective merge update strategy");
                yield updateWithNewMerging(userId, staleStays, startOfDay, endOfDay);
            }
            case FULL_REGENERATION -> {
                log.debug("Using full regeneration strategy");
                yield regenerateFromScratch(userId, startOfDay, endOfDay);
            }
        };
    }
    
    /**
     * Determine the most efficient regeneration strategy based on the type of changes.
     */
    private RegenerationStrategy determineOptimalStrategy(UUID userId, List<TimelineStayEntity> staleStays, Instant date) {
        if (staleStays.isEmpty()) {
            return RegenerationStrategy.FULL_REGENERATION;
        }
        
        // Check if only location resolution is affected (simple case)
        boolean onlyLocationResolutionAffected = staleStays.stream()
            .allMatch(this::isOnlyLocationResolutionChange);
        
        if (onlyLocationResolutionAffected) {
            return RegenerationStrategy.LOCATION_RESOLUTION_ONLY;
        }
        
        // Check if merge logic might be affected (area favorites)
        boolean mergeLogicAffected = isMergeLogicAffected(userId, date);
        
        if (mergeLogicAffected) {
            return RegenerationStrategy.SELECTIVE_MERGE_UPDATE;
        }
        
        // Default to full regeneration for complex cases
        return RegenerationStrategy.FULL_REGENERATION;
    }
    
    /**
     * Check if only location resolution changed (no spatial/temporal changes).
     */
    private boolean isOnlyLocationResolutionChange(TimelineStayEntity stay) {
        // If the stay has a favorite reference but it's stale, it's likely just a location name change
        // This is a heuristic - in practice, this would need more sophisticated detection
        return stay.getFavoriteLocation() != null;
    }
    
    /**
     * Check if merge logic could be affected by area favorites.
     */
    private boolean isMergeLogicAffected(UUID userId, Instant date) {
        // This is a simplified check - in practice, you'd analyze if any area favorites
        // were added that could cause previously separate stays to be merged
        // For now, we'll be conservative and assume merge logic is affected
        return true;
    }
    
    /**
     * Update only location resolution for stays (fastest strategy).
     */
    private MovementTimelineDTO updateLocationResolutionOnly(UUID userId, List<TimelineStayEntity> staleStays, Instant date) {
        log.info("Updating location resolution for {} stale stays", staleStays.size());
        
        for (TimelineStayEntity stay : staleStays) {
            try {
                Point stayPoint = createPoint(stay.getLongitude(), stay.getLatitude());
                LocationResolutionResult newResolution = timelineDataService.resolveLocationWithReferences(userId, stayPoint);
                
                // Update stay with new resolution
                updateStayWithNewResolution(stay, newResolution);
                
                log.debug("Updated location resolution for stay {}: {}", stay.getId(), newResolution.getLocationName());
                
            } catch (Exception e) {
                log.error("Failed to update location resolution for stay {}: {}", stay.getId(), e.getMessage(), e);
                // Mark as failed but continue with others
                stay.setIsStale(true); // Keep as stale for retry
            }
        }
        
        stayRepository.persist(staleStays);
        
        // Return timeline built from all stays for the date
        List<TimelineStayEntity> allStays = stayRepository.findByUserAndDate(userId, date);
        return buildTimelineFromEntities(allStays);
    }
    
    /**
     * Update stays with new resolution and mark as fresh.
     */
    private void updateStayWithNewResolution(TimelineStayEntity stay, LocationResolutionResult resolution) {
        stay.setLocationName(resolution.getLocationName());
        
        if (resolution.getFavoriteId() != null) {
            var favoriteEntity = favoritesRepository.findById(resolution.getFavoriteId());
            stay.setFavoriteLocation(favoriteEntity);
            stay.setGeocodingLocation(null);
            stay.setLocationSource(LocationSource.FAVORITE);
        } else if (resolution.getGeocodingId() != null) {
            var geocodingEntity = geocodingRepository.findById(resolution.getGeocodingId());
            stay.setGeocodingLocation(geocodingEntity);
            stay.setFavoriteLocation(null);
            stay.setLocationSource(LocationSource.GEOCODING);
        }
        
        stay.setIsStale(false);
        stay.setLastUpdated(Instant.now());
        
        // Update version hash
        String newVersion = versionService.generateTimelineVersion(stay.getUser().getId(), stay.getTimestamp());
        stay.setTimelineVersion(newVersion);
    }
    
    /**
     * Regenerate with new merging logic (moderate strategy).
     */
    private MovementTimelineDTO updateWithNewMerging(UUID userId, List<TimelineStayEntity> staleStays, Instant startOfDay, Instant endOfDay) {
        log.info("Updating timeline with new merging logic for {} stale stays", staleStays.size());
        
        // Check if this is primarily an area favorite addition that could merge existing stays
        boolean couldCauseMerging = staleStays.stream()
            .anyMatch(stay -> stay.getFavoriteLocation() == null); // Previously geocoded, now might be favorite
            
        if (couldCauseMerging && staleStays.size() <= 10) {
            // For small numbers of affected stays, try intelligent partial update
            return attemptPartialMergeUpdate(userId, staleStays, startOfDay, endOfDay);
        } else {
            // For complex cases or large numbers of stays, full regeneration is more reliable
            log.debug("Using full regeneration due to complexity or large number of affected stays");
            return regenerateFromScratch(userId, startOfDay, endOfDay);
        }
    }
    
    /**
     * Attempt partial merge update for small numbers of affected stays.
     */
    private MovementTimelineDTO attemptPartialMergeUpdate(UUID userId, List<TimelineStayEntity> staleStays, Instant startOfDay, Instant endOfDay) {
        log.debug("Attempting partial merge update for {} stays", staleStays.size());
        
        try {
            // First update location resolutions for stale stays
            for (TimelineStayEntity stay : staleStays) {
                Point stayPoint = createPoint(stay.getLongitude(), stay.getLatitude());
                LocationResolutionResult newResolution = timelineDataService.resolveLocationWithReferences(userId, stayPoint);
                updateStayWithNewResolution(stay, newResolution);
            }
            
            // Get all stays for the day to check for potential merging opportunities
            List<TimelineStayEntity> allStays = stayRepository.findByUserAndDate(userId, startOfDay);
            
            // Simple merge check: if we have stays with same favorite location within short time/distance, mark for regeneration
            boolean needsFullRegeneration = detectMergeOpportunities(allStays);
            
            if (needsFullRegeneration) {
                log.debug("Detected merge opportunities, falling back to full regeneration");
                return regenerateFromScratch(userId, startOfDay, endOfDay);
            }
            
            // No merging needed, just return updated timeline
            stayRepository.persist(staleStays);
            return buildTimelineFromEntities(allStays);
            
        } catch (Exception e) {
            log.warn("Partial merge update failed, falling back to full regeneration: {}", e.getMessage());
            return regenerateFromScratch(userId, startOfDay, endOfDay);
        }
    }
    
    /**
     * Detect if there are opportunities to merge stays (simple heuristic).
     */
    private boolean detectMergeOpportunities(List<TimelineStayEntity> stays) {
        // Simple check: if we have multiple stays with same favorite location within 2 hours, they might merge
        for (int i = 0; i < stays.size() - 1; i++) {
            for (int j = i + 1; j < stays.size(); j++) {
                TimelineStayEntity stay1 = stays.get(i);
                TimelineStayEntity stay2 = stays.get(j);
                
                // Check if both have same favorite location
                if (stay1.getFavoriteLocation() != null && stay2.getFavoriteLocation() != null &&
                    stay1.getFavoriteLocation().getId().equals(stay2.getFavoriteLocation().getId())) {
                    
                    // Check if they're within 2 hours
                    long hoursBetween = Math.abs(stay1.getTimestamp().getEpochSecond() - stay2.getTimestamp().getEpochSecond()) / 3600;
                    if (hoursBetween <= 2) {
                        log.debug("Found potential merge opportunity between stays {} and {}", stay1.getId(), stay2.getId());
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Full regeneration from scratch (fallback strategy) for a specific date.
     */
    private MovementTimelineDTO regenerateFromScratch(UUID userId, Instant startOfDay, Instant endOfDay) {
        log.info("Performing date-specific timeline regeneration for user {} from {} to {}", userId, startOfDay, endOfDay);
        
        // Delete existing persisted data for ONLY this specific date range
        // FIXED: Previously this deleted ALL historical data with deleteByUserBeforeDate()
        persistenceService.clearTimelineForRange(userId, startOfDay, endOfDay);
        log.debug("Cleared existing timeline data for specific date range {} to {}", startOfDay, endOfDay);
        
        // Generate fresh timeline using existing service
        MovementTimelineDTO freshTimeline = timelineService.getMovementTimeline(userId, startOfDay, endOfDay);
        
        // Persist the fresh timeline (if it's not today)
        if (persistenceService.shouldPersistTimeline(startOfDay)) {
            persistenceService.persistTimelineIfComplete(userId, startOfDay, freshTimeline);
            log.debug("Persisted fresh timeline with {} stays and {} trips", 
                     freshTimeline.getStays().size(), freshTimeline.getTrips().size());
        }
        
        return freshTimeline;
    }
    
    /**
     * Build timeline DTO from persisted entities.
     */
    private MovementTimelineDTO buildTimelineFromEntities(List<TimelineStayEntity> stays) {
        if (stays.isEmpty()) {
            // Need userId - this case should rarely happen but we need to handle it
            log.warn("Building timeline from empty stays list - this may indicate a data issue");
            return null; // Caller should handle this case
        }
        
        UUID userId = stays.getFirst().getUser().getId();
        
        // Get the date range for trips based on stays
        Instant minTime = stays.stream().map(TimelineStayEntity::getTimestamp).min(Instant::compareTo).orElse(Instant.now());
        Instant maxTime = stays.stream().map(TimelineStayEntity::getTimestamp).max(Instant::compareTo).orElse(Instant.now());
        
        // Fetch trips for the same time range
        var trips = tripRepository.findByUserAndDateRange(userId, minTime, maxTime);
        
        return persistenceMapper.toMovementTimelineDTO(userId, stays, trips);
    }
    
    /**
     * Create a Point from coordinates.
     */
    private Point createPoint(double longitude, double latitude) {
        return GeoUtils.createPoint(longitude, latitude);
    }
    
    /**
     * Regenerate the entire timeline for a user from scratch.
     * This is used when favorite changes affect multiple historical dates to avoid
     * cascading data loss from multiple overlapping regenerations.
     * 
     * @param userId user ID
     * @return regenerated timeline (may be null if no data)
     */
    private MovementTimelineDTO regenerateFullUserTimeline(UUID userId) {
        log.info("Performing full timeline regeneration for user {}", userId);
        
        try {
            // Step 1: Delete ALL existing timeline data for the user
            long deletedStays = stayRepository.delete("user.id = ?1", userId);
            long deletedTrips = tripRepository.delete("user.id = ?1", userId);
            
            log.info("Deleted all existing timeline data for user {}: {} stays, {} trips", 
                    userId, deletedStays, deletedTrips);
            
            // Step 2: Find the full date range of GPS data for this user
            // This would require access to GPS data repository to find min/max dates
            // For now, we'll regenerate a reasonable historical range (e.g., last 2 years)
            LocalDate endDate = LocalDate.now(ZoneOffset.UTC);
            LocalDate startDate = endDate.minusYears(2); // Regenerate last 2 years
            
            Instant startTime = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant endTime = endDate.atStartOfDay(ZoneOffset.UTC).toInstant();
            
            log.info("Regenerating full timeline for user {} from {} to {}", userId, startDate, endDate);
            
            // Step 3: Generate fresh timeline for the entire range
            MovementTimelineDTO fullTimeline = timelineService.getMovementTimeline(userId, startTime, endTime);
            
            if (fullTimeline == null) {
                log.info("No timeline data generated for user {} in full regeneration", userId);
                return null;
            }
            
            // Step 4: Persist the regenerated timeline data in daily chunks
            // We need to break it down by day for proper persistence
            persistTimelineInDailyChunks(userId, fullTimeline, startDate, endDate);
            
            log.info("Successfully completed full timeline regeneration for user {}: {} stays, {} trips", 
                    userId, fullTimeline.getStaysCount(), fullTimeline.getTripsCount());
            
            return fullTimeline;
            
        } catch (Exception e) {
            log.error("Failed to perform full timeline regeneration for user {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Persist timeline data in daily chunks for proper date-based organization.
     */
    private void persistTimelineInDailyChunks(UUID userId, MovementTimelineDTO timeline, 
                                            LocalDate startDate, LocalDate endDate) {
        log.debug("Persisting timeline data in daily chunks from {} to {}", startDate, endDate);
        
        // Group stays and trips by date and persist each day separately
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            Instant dayStart = currentDate.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant dayEnd = currentDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            
            // Filter stays for this day
            var dailyStays = timeline.getStays().stream()
                    .filter(stay -> !stay.getTimestamp().isBefore(dayStart) && stay.getTimestamp().isBefore(dayEnd))
                    .collect(Collectors.toList());
            
            // Filter trips for this day  
            var dailyTrips = timeline.getTrips().stream()
                    .filter(trip -> !trip.getTimestamp().isBefore(dayStart) && trip.getTimestamp().isBefore(dayEnd))
                    .collect(Collectors.toList());
            
            // Only persist if there's data for this day and it's a past day
            if ((!dailyStays.isEmpty() || !dailyTrips.isEmpty()) && 
                persistenceService.shouldPersistTimeline(dayStart)) {
                
                MovementTimelineDTO dailyTimeline = new MovementTimelineDTO(userId, dailyStays, dailyTrips);
                persistenceService.persistTimelineIfComplete(userId, dayStart, dailyTimeline);
                log.debug("Persisted timeline for {}: {} stays, {} trips", 
                         currentDate, dailyStays.size(), dailyTrips.size());
            }
            
            currentDate = currentDate.plusDays(1);
        }
    }
    
    /**
     * Regeneration strategies in order of efficiency.
     */
    public enum RegenerationStrategy {
        LOCATION_RESOLUTION_ONLY,    // Fastest - just update location names
        SELECTIVE_MERGE_UPDATE,      // Moderate - re-merge affected segments  
        FULL_REGENERATION           // Slowest - regenerate everything
    }
}