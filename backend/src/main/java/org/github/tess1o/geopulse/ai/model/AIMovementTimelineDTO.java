package org.github.tess1o.geopulse.ai.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AI-optimized DTO representing a complete movement timeline.
 * Contains enriched location data with city/country information
 * and trip data without GPS paths for better AI processing.
 */
@Data
@NoArgsConstructor
public class AIMovementTimelineDTO {
    private UUID userId;
    private List<AITimelineStayDTO> stays;
    private List<AITimelineTripDTO> trips;
    
    private Instant lastUpdated;

    public AIMovementTimelineDTO(UUID userId) {
        this.userId = userId;
        this.stays = new ArrayList<>();
        this.trips = new ArrayList<>();
    }

    public AIMovementTimelineDTO(UUID userId, List<AITimelineStayDTO> stays, List<AITimelineTripDTO> trips) {
        this.userId = userId;
        this.stays = stays;
        this.trips = trips;
    }

    public int getStaysCount() {
        return stays != null ? stays.size() : 0;
    }

    public int getTripsCount() {
        return trips != null ? trips.size() : 0;
    }
}