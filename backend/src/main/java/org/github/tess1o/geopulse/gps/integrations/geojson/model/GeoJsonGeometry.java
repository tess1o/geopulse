package org.github.tess1o.geopulse.gps.integrations.geojson.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Base class for GeoJSON geometry types.
 * Supports Point, MultiPoint, and LineString geometries for GPS data.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = GeoJsonPoint.class, name = "Point"),
    @JsonSubTypes.Type(value = GeoJsonMultiPoint.class, name = "MultiPoint"),
    @JsonSubTypes.Type(value = GeoJsonLineString.class, name = "LineString")
})
public abstract class GeoJsonGeometry {

    /**
     * Geometry type: "Point", "MultiPoint", or "LineString"
     */
    private String type;

    protected GeoJsonGeometry(String type) {
        this.type = type;
    }
}
