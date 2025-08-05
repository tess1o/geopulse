package org.github.tess1o.geopulse.timeline.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.geocoding.service.GeocodingService;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.timeline.model.LocationSource;
import org.github.tess1o.geopulse.timeline.model.TimelineStayEntity;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
@Slf4j
public class FavoriteDeletionHandler {
    
    private final GeocodingService geocodingService;
    private final TimelineStayRepository stayRepository;
    private final ReverseGeocodingLocationRepository geocodingRepository;
    
    public FavoriteDeletionHandler(GeocodingService geocodingService, 
                                   TimelineStayRepository stayRepository,
                                   ReverseGeocodingLocationRepository geocodingRepository) {
        this.geocodingService = geocodingService;
        this.stayRepository = stayRepository;
        this.geocodingRepository = geocodingRepository;
    }

    @Transactional
    public void handleDeletion(List<TimelineStayEntity> affectedStays, FavoriteDeletionStrategy strategy) {
        switch (strategy) {
            case REVERT_TO_GEOCODING -> revertToGeocoding(affectedStays);
            case PRESERVE_HISTORICAL -> preserveHistoricalNames(affectedStays);
            case ASK_USER -> throw new UnsupportedOperationException("User interaction required");
        }
    }
    
    private void revertToGeocoding(List<TimelineStayEntity> stays) {
        log.info("Reverting {} timeline stays to geocoding after favorite deletion", stays.size());
        
        for (TimelineStayEntity stay : stays) {
            try {
                Point stayPoint = GeoUtils.createPoint(stay.getLongitude(), stay.getLatitude());
                FormattableGeocodingResult geocodingResult = geocodingService.getLocationName(stayPoint);
                
                stay.setFavoriteLocation(null);
                stay.setGeocodingLocation(findOrCreateGeocodingEntity(geocodingResult));
                stay.setLocationName(geocodingResult.getFormattedDisplayName());
                stay.setLocationSource(LocationSource.GEOCODING);
                stay.setLastUpdated(Instant.now());

                log.debug("Reverted stay {} to geocoding: {}", stay.getId(), geocodingResult.getFormattedDisplayName());
                
            } catch (Exception e) {
                log.warn("Failed to revert stay {} to geocoding, preserving historical name", stay.getId(), e);
                // Fallback to historical preservation
                stay.setFavoriteLocation(null);
                stay.setGeocodingLocation(null);
                stay.setLocationSource(LocationSource.HISTORICAL);
                stay.setLastUpdated(Instant.now());
                // locationName stays as-is
            }
        }
        stayRepository.persist(stays);
        log.info("Successfully processed {} timeline stays after favorite deletion", stays.size());
    }
    
    private void preserveHistoricalNames(List<TimelineStayEntity> stays) {
        log.info("Preserving historical names for {} timeline stays after favorite deletion", stays.size());
        
        for (TimelineStayEntity stay : stays) {
            stay.setFavoriteLocation(null);  // Clear broken reference
            stay.setLocationSource(LocationSource.HISTORICAL);
            stay.setLastUpdated(Instant.now());
            // locationName preserved as-is
            
            log.debug("Preserved historical name for stay {}: {}", stay.getId(), stay.getLocationName());
        }
        stayRepository.persist(stays);
        log.info("Successfully preserved historical names for {} timeline stays", stays.size());
    }
    
    private ReverseGeocodingLocationEntity findOrCreateGeocodingEntity(FormattableGeocodingResult geocodingResult) {
        Point requestPoint = geocodingResult.getRequestCoordinates();
        Point resultPoint = geocodingResult.getResultCoordinates();
        
        // Try to find existing geocoding entity within 50 meters
        ReverseGeocodingLocationEntity existing = geocodingRepository.findByRequestCoordinates(requestPoint, 50.0);
        if (existing != null) {
            log.debug("Found existing geocoding entity for: {}", geocodingResult.getFormattedDisplayName());
            return existing;
        }
        
        // Create new geocoding entity
        ReverseGeocodingLocationEntity newEntity = new ReverseGeocodingLocationEntity();
        newEntity.setRequestCoordinates(requestPoint);
        newEntity.setResultCoordinates(resultPoint);
        newEntity.setBoundingBox(geocodingResult.getBoundingBox());
        newEntity.setDisplayName(geocodingResult.getFormattedDisplayName());
        newEntity.setProviderName(geocodingResult.getProviderName());
        newEntity.setCity(geocodingResult.getCity());
        newEntity.setCountry(geocodingResult.getCountry());
        
        geocodingRepository.persist(newEntity);
        log.debug("Created new geocoding entity for: {}", geocodingResult.getFormattedDisplayName());
        
        return newEntity;
    }
}