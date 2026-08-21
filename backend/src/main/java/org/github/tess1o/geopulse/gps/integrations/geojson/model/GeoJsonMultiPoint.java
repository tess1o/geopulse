package org.github.tess1o.geopulse.gps.integrations.geojson.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * GeoJSON MultiPoint geometry.
 * Coordinates format: [[lon1, lat1], [lon2, lat2], ...]
 * or [[lon1, lat1, alt1], [lon2, lat2, alt2], ...]
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName("MultiPoint")
public class GeoJsonMultiPoint extends GeoJsonGeometry {

    /**
     * MultiPoint coordinates: array of [longitude, latitude] or [longitude, latitude, altitude]
     */
    private List<List<Double>> coordinates;

    public GeoJsonMultiPoint(List<List<Double>> coordinates) {
        super("MultiPoint");
        this.coordinates = coordinates;
    }

    /**
     * Get all valid coordinate points
     */
    public List<GeoJsonPoint> getPoints() {
        List<GeoJsonPoint> points = new ArrayList<>();
        if (coordinates != null) {
            for (List<Double> coord : coordinates) {
                if (coord != null && coord.size() >= 2) {
                    GeoJsonPoint point = new GeoJsonPoint();
                    point.setCoordinates(coord);
                    if (point.hasValidCoordinates()) {
                        points.add(point);
                    }
                }
            }
        }
        return points;
    }

    /**
     * Check if this MultiPoint has valid coordinates
     */
    public boolean hasValidCoordinates() {
        return coordinates != null &&
               !coordinates.isEmpty() &&
               !getPoints().isEmpty();
    }
}
