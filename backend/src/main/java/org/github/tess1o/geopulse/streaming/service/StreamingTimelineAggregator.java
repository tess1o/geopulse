package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.ai.model.*;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineDataGapDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.service.converters.StreamingTimelineConverter;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class StreamingTimelineAggregator {

    @Inject
    TimelineStayRepository timelineStayRepository;

    @Inject
    TimelineTripRepository timelineTripRepository;

    @Inject
    TimelineDataGapRepository timelineDataGapRepository;

    @Inject
    StreamingTimelineConverter converter;

    public MovementTimelineDTO getTimelineFromDb(UUID userId, Instant startTime, Instant endTime) {
        return getExistingTimelineEvents(userId, startTime, endTime);
    }

    /**
     * Get AI-optimized timeline data with enriched location information.
     * Includes city/country data from joins and trip origin/destination names.
     * 
     * @param userId user ID
     * @param startTime start of time range
     * @param endTime end of time range
     * @return AI-optimized timeline with enriched data
     */
    public AIMovementTimelineDTO getTimelineForAI(UUID userId, Instant startTime, Instant endTime) {
        log.debug("Retrieving AI timeline events for user {} from {} to {}", userId, startTime, endTime);

        AIMovementTimelineDTO timeline = new AIMovementTimelineDTO(userId);
        timeline.setLastUpdated(Instant.now());

        // Get stays with city/country information via SQL joins
        var aiStays = timelineStayRepository.findAITimelineStaysWithLocationData(userId, startTime, endTime);
        timeline.setStays(aiStays);

        // Get trips without GPS path data
        var aiTrips = timelineTripRepository.findAITimelineTripsWithoutPath(userId, startTime, endTime);
        
        // Populate origin/destination information for trips
        populateOriginDestination(aiTrips, aiStays);
        
        timeline.setTrips(aiTrips);

        log.debug("Retrieved {} AI stays, {} AI trips", timeline.getStaysCount(), timeline.getTripsCount());

        return timeline;
    }

    /**
     * Populate origin and destination location names for trips based on nearby stays.
     * Origin: stay that ended closest to (but before) the trip start
     * Destination: stay that started closest to (but after) the trip end
     * 
     * @param aiTrips list of AI trips to populate
     * @param aiStays list of AI stays to use for origin/destination lookup
     */
    private void populateOriginDestination(java.util.List<AITimelineTripDTO> aiTrips, java.util.List<AITimelineStayDTO> aiStays) {
        for (AITimelineTripDTO trip : aiTrips) {
            Instant tripStart = trip.getTimestamp();
            Instant tripEnd = tripStart.plusSeconds(trip.getTripDuration());

            // Find origin: stay that ended closest to (but before) trip start
            AITimelineStayDTO origin = null;
            long minOriginGap = Long.MAX_VALUE;
            
            for (AITimelineStayDTO stay : aiStays) {
                Instant stayEnd = stay.getTimestamp().plusSeconds(stay.getStayDurationSeconds());
                if (stayEnd.isBefore(tripStart) || stayEnd.equals(tripStart)) {
                    long gap = java.time.Duration.between(stayEnd, tripStart).toSeconds();
                    if (gap < minOriginGap) {
                        minOriginGap = gap;
                        origin = stay;
                    }
                }
            }

            // Find destination: stay that started closest to (but after) trip end
            AITimelineStayDTO destination = null;
            long minDestinationGap = Long.MAX_VALUE;
            
            for (AITimelineStayDTO stay : aiStays) {
                Instant stayStart = stay.getTimestamp();
                if (stayStart.isAfter(tripEnd) || stayStart.equals(tripEnd)) {
                    long gap = java.time.Duration.between(tripEnd, stayStart).toSeconds();
                    if (gap < minDestinationGap) {
                        minDestinationGap = gap;
                        destination = stay;
                    }
                }
            }

            // Set origin and destination names
            if (origin != null) {
                trip.setOriginLocationName(origin.getLocationName());
            }
            if (destination != null) {
                trip.setDestinationLocationName(destination.getLocationName());
            }
        }
    }

    private MovementTimelineDTO getExistingTimelineEvents(UUID userId, Instant startTime, Instant endTime) {
        log.debug("Retrieving existing timeline events for user {} from {} to {}", userId, startTime, endTime);

        MovementTimelineDTO timeline = new MovementTimelineDTO(userId);
        timeline.setLastUpdated(Instant.now());

        // Get stays with boundary expansion
        var stayEntities = timelineStayRepository.findByUserIdAndTimeRangeWithExpansion(userId, startTime, endTime);
        for (var stayEntity : stayEntities) {
            TimelineStayLocationDTO stayDTO = converter.convertStayEntityToDto(stayEntity);
            timeline.getStays().add(stayDTO);
        }

        // Get trips with boundary expansion
        var tripEntities = timelineTripRepository.findByUserIdAndTimeRangeWithExpansion(userId, startTime, endTime);
        for (var tripEntity : tripEntities) {
            TimelineTripDTO tripDTO = converter.convertTripEntityToDto(tripEntity);
            timeline.getTrips().add(tripDTO);
        }

        // Get data gaps with boundary expansion
        var gapEntities = timelineDataGapRepository.findByUserIdAndTimeRangeWithExpansion(userId, startTime, endTime);
        for (var gapEntity : gapEntities) {
            var gapDTO = new TimelineDataGapDTO(
                    gapEntity.getStartTime(), gapEntity.getEndTime());
            timeline.getDataGaps().add(gapDTO);
        }

        log.debug("Retrieved {} stays, {} trips, {} data gaps",
                timeline.getStaysCount(), timeline.getTripsCount(), timeline.getDataGapsCount());

        return timeline;
    }

    /**
     * Get aggregated stay statistics grouped by the specified criteria.
     * 
     * @param userId    user ID
     * @param startTime start of time range
     * @param endTime   end of time range
     * @param groupBy   how to group the statistics
     * @return list of stay statistics ordered by significance
     */
    public List<AIStayStatsDTO> getStayStats(UUID userId, Instant startTime, Instant endTime, StayGroupBy groupBy) {
        log.debug("Getting stay statistics for user {} from {} to {} grouped by {}", userId, startTime, endTime, groupBy);
        
        List<AIStayStatsDTO> stats = timelineStayRepository.findStayStatistics(userId, startTime, endTime, groupBy);
        
        log.debug("Retrieved {} stay statistics groups", stats.size());
        return stats;
    }

    /**
     * Get aggregated trip statistics grouped by the specified criteria.
     * Handles complex origin/destination grouping at the service layer.
     * 
     * @param userId    user ID
     * @param startTime start of time range
     * @param endTime   end of time range
     * @param groupBy   how to group the statistics
     * @return list of trip statistics ordered by significance
     */
    public List<AITripStatsDTO> getTripStats(UUID userId, Instant startTime, Instant endTime, TripGroupBy groupBy) {
        log.debug("Getting trip statistics for user {} from {} to {} grouped by {}", userId, startTime, endTime, groupBy);
        
        // For simple groupings, delegate to repository
        if (groupBy != TripGroupBy.ORIGIN_LOCATION_NAME && groupBy != TripGroupBy.DESTINATION_LOCATION_NAME) {
            List<AITripStatsDTO> stats = timelineTripRepository.findTripStatistics(userId, startTime, endTime, groupBy);
            log.debug("Retrieved {} trip statistics groups", stats.size());
            return stats;
        }
        
        // For origin/destination grouping, we need to compute these at service layer
        return getTripStatsWithOriginDestination(userId, startTime, endTime, groupBy);
    }

    /**
     * Handle complex origin/destination trip statistics by computing origin/destination in memory.
     */
    private List<AITripStatsDTO> getTripStatsWithOriginDestination(UUID userId, Instant startTime, Instant endTime, TripGroupBy groupBy) {
        log.debug("Computing trip statistics with origin/destination grouping");
        
        // Get all trips and stays to compute origin/destination
        var aiTrips = timelineTripRepository.findAITimelineTripsWithoutPath(userId, startTime, endTime);
        var aiStays = timelineStayRepository.findAITimelineStaysWithLocationData(userId, startTime, endTime);
        
        // Populate origin/destination information
        populateOriginDestination(aiTrips, aiStays);
        
        // Group trips by the specified field
        String groupType = groupBy.getValue();
        Map<String, List<AITimelineTripDTO>> groupedTrips = aiTrips.stream()
                .collect(Collectors.groupingBy(trip -> {
                    return switch (groupBy) {
                        case ORIGIN_LOCATION_NAME -> 
                            trip.getOriginLocationName() != null ? trip.getOriginLocationName() : "Unknown Origin";
                        case DESTINATION_LOCATION_NAME -> 
                            trip.getDestinationLocationName() != null ? trip.getDestinationLocationName() : "Unknown Destination";
                        default -> "Unknown";
                    };
                }));
        
        // Calculate statistics for each group
        return groupedTrips.entrySet().stream()
                .map(entry -> {
                    String groupKey = entry.getKey();
                    List<AITimelineTripDTO> trips = entry.getValue();
                    
                    long tripCount = trips.size();
                    long totalDistance = trips.stream().mapToLong(AITimelineTripDTO::getDistanceMeters).sum();
                    long totalDuration = trips.stream().mapToLong(AITimelineTripDTO::getTripDuration).sum();
                    
                    double avgDistance = tripCount > 0 ? (double) totalDistance / tripCount : 0.0;
                    double avgDuration = tripCount > 0 ? (double) totalDuration / tripCount : 0.0;
                    
                    long minDistance = trips.stream().mapToLong(AITimelineTripDTO::getDistanceMeters).min().orElse(0L);
                    long maxDistance = trips.stream().mapToLong(AITimelineTripDTO::getDistanceMeters).max().orElse(0L);
                    long minDuration = trips.stream().mapToLong(AITimelineTripDTO::getTripDuration).min().orElse(0L);
                    long maxDuration = trips.stream().mapToLong(AITimelineTripDTO::getTripDuration).max().orElse(0L);
                    
                    double avgSpeedKmh = totalDuration > 0 ? (totalDistance * 3.6) / totalDuration : 0.0;
                    
                    return AITripStatsDTO.builder()
                        .groupKey(groupKey)
                        .groupType(groupType)
                        .tripCount(tripCount)
                        .totalDistanceMeters(totalDistance)
                        .avgDistanceMeters(avgDistance)
                        .minDistanceMeters(minDistance)
                        .maxDistanceMeters(maxDistance)
                        .totalDurationSeconds(totalDuration)
                        .avgDurationSeconds(avgDuration)
                        .minDurationSeconds(minDuration)
                        .maxDurationSeconds(maxDuration)
                        .avgSpeedKmh(avgSpeedKmh)
                        .build();
                })
                .sorted((a, b) -> Long.compare(b.getTripCount(), a.getTripCount())) // Order by count desc
                .toList();
    }
}
