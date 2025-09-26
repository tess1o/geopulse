package org.github.tess1o.geopulse.ai.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * AI-optimized DTO for timeline trips without GPS path data.
 * Includes origin and destination location names derived from nearby stays.
 */
@Data
@NoArgsConstructor
public class AITimelineTripDTO {
    private Instant timestamp;

    /**
     * Duration of trip in seconds
     */
    private long tripDuration;

    /**
     * Distance traveled in meters
     */
    private long distanceMeters;

    private String movementType;

    /**
     * Origin location name from the stay closest to trip start
     */
    private String originLocationName;

    /**
     * Destination location name from the stay closest to trip end
     */
    private String destinationLocationName;
}