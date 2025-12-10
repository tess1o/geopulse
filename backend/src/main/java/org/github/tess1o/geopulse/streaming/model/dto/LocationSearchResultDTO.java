package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for location search results.
 * Supports searching across places, cities, and countries with type discrimination.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationSearchResultDTO {
    private String type;                // "place", "city", or "country"
    private String name;                // Raw name value
    private String displayName;         // Formatted display name
    private String category;            // For places: "favorite" or "geocoding"
    private Long id;                    // For places only (favorite or geocoding ID)
    private String country;             // For cities
    private int visitCount;             // Preview metric for ranking
}
