package org.github.tess1o.geopulse.timeline.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrackPoint implements GpsPoint {
    private double longitude;
    private double latitude;
    private Instant timestamp;
    private Double accuracy;
    private Double velocity;
}
