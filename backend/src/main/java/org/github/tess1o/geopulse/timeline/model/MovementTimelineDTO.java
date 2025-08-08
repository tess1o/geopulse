package org.github.tess1o.geopulse.timeline.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO representing a complete movement timeline.
 * Contains a list of timeline items and metadata about the timeline.
 */
@Data
@NoArgsConstructor
public class MovementTimelineDTO {
    private UUID userId;
    private List<TimelineStayLocationDTO> stays;
    private List<TimelineTripDTO> trips;

    // Timeline metadata for persistence and caching
    private TimelineDataSource dataSource;     // Source of this timeline data
    private Instant lastUpdated;               // When this data was last generated/updated

    public MovementTimelineDTO(UUID userId) {
        this.userId = userId;
        this.stays = new ArrayList<>();
        this.trips = new ArrayList<>();
    }

    /**
     * Constructor that automatically sets the item count.
     */
    public MovementTimelineDTO(UUID userId, List<TimelineStayLocationDTO> stays, List<TimelineTripDTO> trips) {
        this.userId = userId;
        this.stays = stays;
        this.trips = trips;
    }

    public int getStaysCount(){
        return stays != null ? stays.size() : 0;
    }

    public int getTripsCount(){
        return trips != null ? trips.size() : 0;
    }
}