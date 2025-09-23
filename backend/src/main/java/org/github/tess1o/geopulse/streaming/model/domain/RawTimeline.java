package org.github.tess1o.geopulse.streaming.model.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Container for raw timeline objects (Stay, Trip, DataGap) before DTO conversion.
 * This maintains rich domain data throughout the processing pipeline (merging, path simplification)
 * without degrading GPS data quality through DTO conversions.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RawTimeline {
    
    /**
     * User identifier for this timeline
     */
    private UUID userId;
    
    /**
     * Stay events with rich location data
     */
    @Builder.Default
    private List<Stay> stays = List.of();
    
    /**
     * Trip events with rich GPS data (including speed and accuracy)
     */
    @Builder.Default
    private List<Trip> trips = List.of();
    
    /**
     * Data gap events
     */
    @Builder.Default
    private List<DataGap> dataGaps = List.of();
    
    /**
     * Get total count of all timeline events
     */
    public int getTotalEventCount() {
        return stays.size() + trips.size() + dataGaps.size();
    }
    
    /**
     * Check if timeline has any events
     */
    public boolean isEmpty() {
        return stays.isEmpty() && trips.isEmpty() && dataGaps.isEmpty();
    }
    
    /**
     * Create RawTimeline from list of TimelineEvent objects
     */
    public static RawTimeline fromEvents(UUID userId, List<TimelineEvent> events) {
        RawTimelineBuilder builder = RawTimeline.builder().userId(userId);
        
        List<Stay> stays = new java.util.ArrayList<>();
        List<Trip> trips = new java.util.ArrayList<>();
        List<DataGap> dataGaps = new java.util.ArrayList<>();
        
        for (TimelineEvent event : events) {
            switch (event.getType()) {
                case STAY -> stays.add((Stay) event);
                case TRIP -> trips.add((Trip) event);
                case DATA_GAP -> dataGaps.add((DataGap) event);
            }
        }
        
        return builder
                .stays(stays)
                .trips(trips)
                .dataGaps(dataGaps)
                .build();
    }
}