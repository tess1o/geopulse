package org.github.tess1o.geopulse.gps.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing a single GPS point in a tracking path.
 * Contains the essential data needed for displaying a GPS point on a map.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GpsPointPathPointDTO implements GpsPoint {
    private long id;
    private double longitude;
    private double latitude;
    private Instant timestamp;
    private Double accuracy;
    private Double altitude;
    private Double velocity;
    private UUID userId;
    private String sourceType;
}