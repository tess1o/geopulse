package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO representing place geometry - can be either a point or an area (rectangle).
 * For point: single lat/lon coordinates
 * For area: rectangle bounds with northEast and southWest corners
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceGeometryDTO {

    /**
     * Type of geometry: "point" or "area"
     */
    private String type;

    /**
     * For point type: the latitude
     * For area type: center latitude (optional, for convenience)
     */
    private Double latitude;

    /**
     * For point type: the longitude
     * For area type: center longitude (optional, for convenience)
     */
    private Double longitude;

    /**
     * For area type: north-east corner coordinates [lat, lon]
     */
    private double[] northEast;

    /**
     * For area type: south-west corner coordinates [lat, lon]
     */
    private double[] southWest;

    /**
     * For area type: all corner coordinates as array of [lat, lon] pairs
     * Useful for drawing polygons
     */
    private List<double[]> coordinates;
}
