package org.github.tess1o.geopulse.streaming.model.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;

import java.time.Instant;

/**
 * Lightweight GPS point for streaming timeline processing.
 * Contains only the essential data needed for the state machine algorithm.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GPSPoint implements GpsPoint {

    private Instant timestamp;
    private double latitude;
    private double longitude;
    private double speed; // in meters per second
    private double accuracy; // GPS accuracy in meters

    public GPSPoint(double latitude, double longitude, double speed, double accuracy, Instant timestamp) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.speed = speed;
        this.accuracy = accuracy;
        this.timestamp = timestamp;
    }

    public GPSPoint(double latitude, double longitude, double speed, double accuracy) {
        this(latitude, longitude, speed, accuracy, null);
    }

    /**
     * Calculate the distance in meters between this point and another GPS point.
     * Uses the Haversine formula for great-circle distance calculation.
     *
     * @param other the other GPS point
     * @return distance in meters
     */
    public double distanceTo(GPSPoint other) {
        if (other == null) {
            return 0.0;
        }

        return GeoUtils.haversine(this.latitude, this.longitude, other.latitude, other.longitude);
    }

}