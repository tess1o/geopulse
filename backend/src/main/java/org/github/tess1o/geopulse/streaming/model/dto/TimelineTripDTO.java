package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimelineTripDTO implements GpsPoint {
    private Instant timestamp;
    private double latitude;  // Start latitude
    private double longitude; // Start longitude
    private double endLatitude;  // End latitude  
    private double endLongitude; // End longitude
    
    /**
     * Duration of trip in seconds
     */
    private long tripDuration;
    
    /**
     * Distance traveled in meters
     */
    private long distanceMeters;
    private String movementType;
    private List<? extends GpsPoint> path; // Only used for TRIP items
}
