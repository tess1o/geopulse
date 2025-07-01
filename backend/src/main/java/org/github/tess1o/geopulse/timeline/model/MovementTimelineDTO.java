package org.github.tess1o.geopulse.timeline.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * DTO representing a complete movement timeline.
 * Contains a list of timeline items and metadata about the timeline.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovementTimelineDTO {
    private UUID userId;
    private List<TimelineStayLocationDTO> stays;
    private List<TimelineTripDTO> trips;
    private int staysCount;
    private int tripsCount;
    
    // Timeline metadata for persistence and caching
    private TimelineDataSource dataSource;     // Source of this timeline data
    private Instant lastUpdated;               // When this data was last generated/updated
    private Boolean isStale;                   // If favorite changes affected this timeline

    public MovementTimelineDTO(UUID userId) {
        this.userId = userId;
        this.stays = Collections.emptyList();
        this.trips = Collections.emptyList();
        this.staysCount = 0;
        this.tripsCount = 0;
    }

    /**
     * Constructor that automatically sets the item count.
     */
    public MovementTimelineDTO(UUID userId, List<TimelineStayLocationDTO> stays, List<TimelineTripDTO> trips) {
        this.userId = userId;
        this.stays = stays;
        this.trips = trips;
        this.staysCount = stays != null ? stays.size() : 0;
        this.tripsCount = trips != null ? trips.size() : 0;
    }

}