package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Aggregated place payload optimized for map rendering in Location Analytics.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationAnalyticsMapPlaceDTO {
    private String type;            // "favorite" or "geocoding"
    private Long id;                // Place ID from favorite/geocoding table
    private String locationName;    // Display name
    private long visitCount;        // Number of visits
    private Instant lastVisit;      // Most recent visit timestamp
    private double latitude;        // Aggregated centroid latitude
    private double longitude;       // Aggregated centroid longitude
    private String city;
    private String country;
}
