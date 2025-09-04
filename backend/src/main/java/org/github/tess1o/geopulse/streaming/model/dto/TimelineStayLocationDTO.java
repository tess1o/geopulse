package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimelineStayLocationDTO implements GpsPoint {
    private Instant timestamp;
    
    // Location source references - exactly one will be populated
    private Long favoriteId;        // Reference to FavoritesEntity.id if location came from favorite
    private Long geocodingId;       // Reference to ReverseGeocodingLocationEntity.id if location came from geocoding
    
    // Cached location name for display (resolved at creation time)
    private String locationName;
    
    /**
     * Duration of stay in seconds
     */
    private long stayDuration;
    private double latitude;
    private double longitude;
}
