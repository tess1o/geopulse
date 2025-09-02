package org.github.tess1o.geopulse.streaming.merge;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;

import java.time.Instant;
import java.util.List;

/**
 * Service responsible for executing timeline merges.
 * Creates merged stay objects from multiple stays and calculates combined durations.
 */
@ApplicationScoped
@Slf4j
public class TimelineMergeExecutionService {

    /**
     * Create a merged stay from a group of stays and calculate total duration.
     * The merged stay uses the first stay's properties but combines durations from all stays
     * and any trips between them.
     * 
     * @param stays all stays in the timeline
     * @param mergeGroup indices of stays to merge
     * @param trips all trips for duration calculation
     * @return merged stay with combined duration
     */
    public TimelineStayLocationDTO createMergedStay(List<TimelineStayLocationDTO> stays,
                                                   List<Integer> mergeGroup,
                                                   List<TimelineTripDTO> trips) {
        if (stays == null || mergeGroup == null || mergeGroup.isEmpty()) {
            throw new IllegalArgumentException("Invalid parameters for stay merge");
        }
        
        log.debug("Creating merged stay from {} stays", mergeGroup.size());
        
        TimelineStayLocationDTO firstStay = stays.get(mergeGroup.get(0));
        
        // Calculate total duration of all stays in the group
        long totalDuration = calculateStayGroupDuration(stays, mergeGroup);
        
        // Add duration of trips between merged stays
        long tripDuration = calculateInterStayTripDuration(stays, mergeGroup, trips);
        
        long finalDuration = totalDuration + tripDuration;
        
        log.debug("Merged stay duration: stays={}min, trips={}min, total={}min", 
                 totalDuration, tripDuration, finalDuration);

        return TimelineStayLocationDTO.builder()
                .timestamp(firstStay.getTimestamp())
                .locationName(firstStay.getLocationName())
                .stayDuration(finalDuration)
                .latitude(firstStay.getLatitude())
                .longitude(firstStay.getLongitude())
                .favoriteId(firstStay.getFavoriteId())
                .geocodingId(firstStay.getGeocodingId())
                .build();
    }
    
    /**
     * Calculate the total duration of all stays in a merge group.
     * 
     * @param stays all stays
     * @param mergeGroup indices of stays to include
     * @return total duration in minutes
     */
    public long calculateStayGroupDuration(List<TimelineStayLocationDTO> stays, 
                                         List<Integer> mergeGroup) {
        if (stays == null || mergeGroup == null) {
            return 0;
        }
        
        long totalDuration = 0;
        for (Integer index : mergeGroup) {
            if (index >= 0 && index < stays.size()) {
                totalDuration += stays.get(index).getStayDuration();
            }
        }
        
        log.debug("Total stay group duration: {}min from {} stays", totalDuration, mergeGroup.size());
        return totalDuration;
    }
    
    /**
     * Calculate the total duration of trips between merged stays.
     * This includes all travel time that will be absorbed into the merged stay.
     * 
     * @param stays all stays 
     * @param mergeGroup indices of stays being merged
     * @param trips all trips
     * @return total trip duration in minutes
     */
    public long calculateInterStayTripDuration(List<TimelineStayLocationDTO> stays,
                                             List<Integer> mergeGroup,
                                             List<TimelineTripDTO> trips) {
        if (stays == null || mergeGroup == null || trips == null || mergeGroup.size() < 2) {
            return 0;
        }
        
        long totalTripDuration = 0;
        
        // Add duration of trips between consecutive merged stays
        for (int i = 0; i < mergeGroup.size() - 1; i++) {
            TimelineStayLocationDTO currentStay = stays.get(mergeGroup.get(i));
            TimelineStayLocationDTO nextStay = stays.get(mergeGroup.get(i + 1));

            long tripDuration = findTripDurationBetweenStays(currentStay, nextStay, trips);
            totalTripDuration += tripDuration;
            
            log.debug("Trip duration between stay {} and {}: {}min", 
                     mergeGroup.get(i), mergeGroup.get(i + 1), tripDuration);
        }
        
        log.debug("Total inter-stay trip duration: {}min", totalTripDuration);
        return totalTripDuration;
    }
    
    /**
     * Find the total duration of trips occurring between two specific stays.
     * 
     * @param currentStay first stay
     * @param nextStay second stay
     * @param trips all trips to search
     * @return total duration of trips between the stays in minutes
     */
    public long findTripDurationBetweenStays(TimelineStayLocationDTO currentStay,
                                           TimelineStayLocationDTO nextStay,
                                           List<TimelineTripDTO> trips) {
        if (currentStay == null || nextStay == null || trips == null) {
            return 0;
        }
        
        Instant currentStayTime = currentStay.getTimestamp();
        Instant nextStayTime = nextStay.getTimestamp();
        
        long totalDuration = 0;
        
        for (TimelineTripDTO trip : trips) {
            if (trip.getTimestamp().isAfter(currentStayTime) &&
                    trip.getTimestamp().isBefore(nextStayTime)) {
                totalDuration += trip.getTripDuration();
            }
        }
        
        return totalDuration;
    }
    
}