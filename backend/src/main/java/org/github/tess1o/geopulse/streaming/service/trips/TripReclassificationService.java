package org.github.tess1o.geopulse.streaming.service.trips;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for fast trip type recalculation without full timeline regeneration.
 * Used when only travel classification parameters change (speed thresholds).
 */
@ApplicationScoped
@Slf4j
public class TripReclassificationService {

    private final TimelineTripRepository tripRepository;
    private final TravelClassification travelClassification;
    private final TimelineConfigurationProvider configProvider;

    @Inject
    public TripReclassificationService(TimelineTripRepository tripRepository,
                                       TravelClassification travelClassification,
                                       TimelineConfigurationProvider configProvider) {
        this.tripRepository = tripRepository;
        this.travelClassification = travelClassification;
        this.configProvider = configProvider;
    }

    /**
     * Recalculate trip types for all trips of a user based on updated travel classification settings.
     * This is much faster than full timeline regeneration.
     *
     * @param userId the user whose trips should be reclassified
     */
    @Transactional
    public void reclassifyUserTrips(UUID userId) {
        log.info("Starting trip reclassification for user {}", userId);
        long startTime = System.currentTimeMillis();

        try {
            // Get user's current configuration with updated classification settings
            TimelineConfig config = configProvider.getConfigurationForUser(userId);
            
            // Get all trips for the user
            List<TimelineTripEntity> trips = tripRepository.findByUser(userId);
            log.debug("Found {} trips to reclassify for user {}", trips.size(), userId);

            int updatedCount = 0;
            int skippedCount = 0;
            
            for (TimelineTripEntity trip : trips) {
                try {
                    String oldMovementType = trip.getMovementType();
                    String newMovementType = reclassifyTrip(trip, config);
                    
                    if (!newMovementType.equals(oldMovementType)) {
                        trip.setMovementType(newMovementType);
                        trip.setLastUpdated(Instant.now());
                        updatedCount++;
                        
                        if (log.isDebugEnabled()) {
                            log.debug("Trip {} reclassified from {} to {} (distance: {}m, duration: {}s)", 
                                trip.getId(), oldMovementType, newMovementType, 
                                trip.getDistanceMeters(), trip.getTripDuration());
                        }
                    } else {
                        skippedCount++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to reclassify trip {} for user {}: {}", trip.getId(), userId, e.getMessage());
                    skippedCount++;
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Trip reclassification completed for user {} in {}ms: {} updated, {} unchanged", 
                userId, duration, updatedCount, skippedCount);
                
        } catch (Exception e) {
            log.error("Failed to reclassify trips for user {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Reclassify a single trip using enhanced GPS statistics if available.
     *
     * @param trip the trip to reclassify
     * @param config the timeline configuration with classification settings
     * @return the new movement type
     */
    private String reclassifyTrip(TimelineTripEntity trip, TimelineConfig config) {
        // Use enhanced classification with GPS statistics if available
        TripType tripType = travelClassification.classifyTravelType(trip, config);
        
        return tripType.name();
    }

}