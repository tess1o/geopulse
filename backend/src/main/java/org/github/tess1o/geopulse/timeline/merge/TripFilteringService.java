package org.github.tess1o.geopulse.timeline.merge;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.timeline.model.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineTripDTO;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Service responsible for filtering trips during timeline merging.
 * Handles identification and removal of trips that are absorbed by merged stays.
 */
@ApplicationScoped
@Slf4j
public class TripFilteringService {

    /**
     * Mark trips between merged stays for removal.
     * These trips will be absorbed into the duration of the merged stay.
     * 
     * @param mergeGroup indices of stays being merged
     * @param stays all stays in the timeline
     * @param trips all trips in the timeline
     * @return set of trip indices to remove
     */
    public Set<Integer> markTripsForRemovalInMergeGroup(List<Integer> mergeGroup,
                                                       List<TimelineStayLocationDTO> stays,
                                                       List<TimelineTripDTO> trips) {
        if (mergeGroup == null || mergeGroup.size() < 2 || stays == null || trips == null) {
            return new HashSet<>();
        }
        
        log.debug("Marking trips for removal between {} merged stays", mergeGroup.size());
        
        Set<Integer> removedTripIndices = new HashSet<>();
        
        // Mark trips between consecutive merged stays for removal
        for (int i = 0; i < mergeGroup.size() - 1; i++) {
            TimelineStayLocationDTO currentStay = stays.get(mergeGroup.get(i));
            TimelineStayLocationDTO nextStay = stays.get(mergeGroup.get(i + 1));
            
            Set<Integer> tripsBetweenStays = findTripsBetweenStays(currentStay, nextStay, trips);
            removedTripIndices.addAll(tripsBetweenStays);
            
            log.debug("Found {} trips to remove between stay {} and stay {}", 
                     tripsBetweenStays.size(), mergeGroup.get(i), mergeGroup.get(i + 1));
        }
        
        log.debug("Total trips marked for removal: {}", removedTripIndices.size());
        return removedTripIndices;
    }
    
    /**
     * Find trips that occur between two specific stays chronologically.
     * 
     * @param firstStay first stay
     * @param secondStay second stay
     * @param trips all trips to search
     * @return set of indices of trips that occur between the stays
     */
    public Set<Integer> findTripsBetweenStays(TimelineStayLocationDTO firstStay,
                                            TimelineStayLocationDTO secondStay,
                                            List<TimelineTripDTO> trips) {
        if (firstStay == null || secondStay == null || trips == null) {
            return new HashSet<>();
        }
        
        Instant firstStayTime = firstStay.getTimestamp();
        Instant secondStayTime = secondStay.getTimestamp();
        
        Set<Integer> tripIndices = new HashSet<>();
        
        for (int i = 0; i < trips.size(); i++) {
            TimelineTripDTO trip = trips.get(i);
            
            // A trip between stays should be after the first stay starts and before the second stay starts
            if (isTripBetweenTimes(trip, firstStayTime, secondStayTime)) {
                tripIndices.add(i);
                log.debug("Trip at index {} occurs between stays: {}", i, trip.getTimestamp());
            }
        }
        
        return tripIndices;
    }
    
    /**
     * Check if a trip occurs between two timestamps.
     * 
     * @param trip trip to check
     * @param startTime start boundary (exclusive)
     * @param endTime end boundary (exclusive) 
     * @return true if trip timestamp is between the boundaries
     */
    public boolean isTripBetweenTimes(TimelineTripDTO trip, Instant startTime, Instant endTime) {
        if (trip == null || startTime == null || endTime == null) {
            return false;
        }
        
        return trip.getTimestamp().isAfter(startTime) && 
               trip.getTimestamp().isBefore(endTime);
    }
    
    /**
     * Filter trips to remove those marked for deletion.
     * Creates a new list containing only trips that should be retained.
     * 
     * @param trips original list of trips
     * @param removedIndices set of indices to remove
     * @return filtered list of trips
     */
    public List<TimelineTripDTO> filterTrips(List<TimelineTripDTO> trips, 
                                           Set<Integer> removedIndices) {
        if (trips == null || removedIndices == null) {
            return trips;
        }
        
        log.debug("Filtering {} trips, removing {} marked for deletion", 
                 trips.size(), removedIndices.size());
        
        List<TimelineTripDTO> filteredTrips = IntStream.range(0, trips.size())
                .filter(i -> !removedIndices.contains(i))
                .mapToObj(trips::get)
                .toList();
        
        log.debug("Filtered trips: {} remaining after removing {}", 
                 filteredTrips.size(), removedIndices.size());
        
        return filteredTrips;
    }
    
}