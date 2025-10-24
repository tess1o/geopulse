package org.github.tess1o.geopulse.gps.integrations.geojson.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GeoJSON Point geometry.
 * Coordinates format: [longitude, latitude] or [longitude, latitude, altitude]
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName("Point")
public class GeoJsonPoint extends GeoJsonGeometry {

    /**
     * Point coordinates: [longitude, latitude] or [longitude, latitude, altitude]
     */
    private List<Double> coordinates;

    public GeoJsonPoint(double longitude, double latitude) {
        super("Point");
        this.coordinates = List.of(longitude, latitude);
    }

    public GeoJsonPoint(double longitude, double latitude, double altitude) {
        super("Point");
        this.coordinates = List.of(longitude, latitude, altitude);
    }

    /**
     * Get longitude (x coordinate)
     */
    public Double getLongitude() {
        return coordinates != null && !coordinates.isEmpty() ? coordinates.get(0) : null;
    }

    /**
     * Get latitude (y coordinate)
     */
    public Double getLatitude() {
        return coordinates != null && coordinates.size() > 1 ? coordinates.get(1) : null;
    }

    /**
     * Get altitude (z coordinate) if present
     */
    public Double getAltitude() {
        return coordinates != null && coordinates.size() > 2 ? coordinates.get(2) : null;
    }

    /**
     * Check if this point has valid coordinates
     */
    public boolean hasValidCoordinates() {
        return coordinates != null &&
               coordinates.size() >= 2 &&
               getLongitude() != null &&
               getLatitude() != null &&
               getLongitude() >= -180 && getLongitude() <= 180 &&
               getLatitude() >= -90 && getLatitude() <= 90;
    }
}
