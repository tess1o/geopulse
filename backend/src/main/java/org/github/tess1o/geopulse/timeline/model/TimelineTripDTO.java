package org.github.tess1o.geopulse.timeline.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimelineTripDTO implements GpsPoint {
    private Instant timestamp;
    private double latitude;
    private double longitude;
    private long tripDuration;
    private double distanceKm;
    private String movementType;
    private List<? extends GpsPoint> path; // Only used for TRIP items
}
