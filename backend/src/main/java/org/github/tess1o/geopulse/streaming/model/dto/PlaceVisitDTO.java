package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO representing a single visit to a specific place.
 * Used in the place details view to show visit history.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceVisitDTO {
    private Long id;                    // Timeline stay ID
    private Instant timestamp;          // Start time of visit
    private long stayDuration;          // Duration in seconds
    private double latitude;
    private double longitude;
    private String locationName;        // Cached location name
}
