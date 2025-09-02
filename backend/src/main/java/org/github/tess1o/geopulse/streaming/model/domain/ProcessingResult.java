package org.github.tess1o.geopulse.streaming.model.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the result of processing a single GPS point through the streaming timeline processor.
 * Contains the updated user state and any timeline events that were finalized during processing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessingResult {
    
    /**
     * The updated user state after processing the GPS point.
     * This state will be used for processing the next GPS point.
     */
    private UserState updatedState;
    
    /**
     * List of timeline events that were completed/finalized during this processing step.
     * Most processing steps will return an empty list, with events only being finalized
     * when state transitions occur (e.g., stay ends, trip ends).
     */
    @Builder.Default
    private List<TimelineEvent> finalizedEvents = new ArrayList<>();
    
    /**
     * Create a processing result with no finalized events.
     *
     * @param updatedState the updated user state
     * @return processing result with empty events list
     */
    public static ProcessingResult withStateOnly(UserState updatedState) {
        return ProcessingResult.builder()
            .updatedState(updatedState)
            .finalizedEvents(new ArrayList<>())
            .build();
    }
    
    /**
     * Create a processing result with a single finalized event.
     *
     * @param updatedState the updated user state
     * @param finalizedEvent the single event that was finalized
     * @return processing result with the single event
     */
    public static ProcessingResult withSingleEvent(UserState updatedState, TimelineEvent finalizedEvent) {
        List<TimelineEvent> events = new ArrayList<>();
        if (finalizedEvent != null) {
            events.add(finalizedEvent);
        }
        
        return ProcessingResult.builder()
            .updatedState(updatedState)
            .finalizedEvents(events)
            .build();
    }
    
    /**
     * Get an immutable view of the finalized events.
     *
     * @return unmodifiable list of finalized events
     */
    public List<TimelineEvent> getFinalizedEvents() {
        return finalizedEvents != null ? Collections.unmodifiableList(finalizedEvents) : Collections.emptyList();
    }
}