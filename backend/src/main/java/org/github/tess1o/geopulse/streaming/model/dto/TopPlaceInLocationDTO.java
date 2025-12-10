package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a top place within a city or country.
 * Used to show the most visited places in location analytics.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopPlaceInLocationDTO {
    private String type;                // "favorite" or "geocoding"
    private Long id;                    // Place ID (favorite or geocoding)
    private String name;                // Place name
    private long visitCount;            // Number of visits to this place
    private long totalDuration;         // Total duration in seconds
    private Double latitude;            // Latitude (optional)
    private Double longitude;           // Longitude (optional)
}
