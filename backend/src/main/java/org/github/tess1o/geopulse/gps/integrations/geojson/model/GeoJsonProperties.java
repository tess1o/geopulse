package org.github.tess1o.geopulse.gps.integrations.geojson.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GeoJSON Feature properties for GPS data.
 * Contains GPS metadata following standard property names for GIS tool compatibility.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeoJsonProperties {

    /**
     * Timestamp in ISO-8601 format (e.g., "2024-01-01T12:00:00Z")
     */
    private String timestamp;

    /**
     * Altitude in meters above sea level
     */
    private Double altitude;

    /**
     * Velocity in km/h
     */
    private Double velocity;

    /**
     * Horizontal accuracy in meters
     */
    private Double accuracy;

    /**
     * Device identifier
     */
    private String deviceId;

    /**
     * GPS source type (e.g., "GEOJSON", "GPX", "OWNTRACKS")
     */
    private String sourceType;

    /**
     * Vertical accuracy in meters (optional)
     */
    private Double verticalAccuracy;

    /**
     * Battery level percentage (0-100, optional)
     */
    private Integer battery;

    /**
     * Course/heading in degrees (0-360, optional)
     */
    private Double course;
}
