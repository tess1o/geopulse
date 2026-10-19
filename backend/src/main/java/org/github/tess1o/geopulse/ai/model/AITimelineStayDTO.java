package org.github.tess1o.geopulse.ai.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * AI-optimized DTO for timeline stay locations with enriched geographic information.
 * Includes city and country data fetched via SQL joins for better AI context.
 */
@Data
@NoArgsConstructor
public class AITimelineStayDTO {
    /**
     * Timeline stay ID, usable with explainTimelineEvent
     */
    private Long id;

    private Instant timestamp;

    private String locationName;

    /**
     * Duration of stay in seconds
     */
    private long stayDurationSeconds;

    /**
     * City name from favorite or geocoding location
     */
    private String city;

    /**
     * Country name from favorite or geocoding location
     */
    private String country;

}