package org.github.tess1o.geopulse.gps.integrations.geojson.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GeoJSON Feature containing geometry and properties.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeoJsonFeature {

    /**
     * Always "Feature" for GeoJSON compliance
     */
    @Builder.Default
    private String type = "Feature";

    /**
     * Feature geometry (Point or LineString)
     */
    private GeoJsonGeometry geometry;

    /**
     * Feature properties containing GPS metadata
     */
    private GeoJsonProperties properties;

    /**
     * Optional feature identifier
     */
    private String id;

    /**
     * Check if this feature has valid geometry
     */
    public boolean hasValidGeometry() {
        if (geometry == null) {
            return false;
        }

        if (geometry instanceof GeoJsonPoint) {
            return ((GeoJsonPoint) geometry).hasValidCoordinates();
        } else if (geometry instanceof GeoJsonLineString) {
            return ((GeoJsonLineString) geometry).hasValidCoordinates();
        }

        return false;
    }
}
