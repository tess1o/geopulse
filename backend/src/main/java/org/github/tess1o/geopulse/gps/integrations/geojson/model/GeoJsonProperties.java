package org.github.tess1o.geopulse.gps.integrations.geojson.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
@JsonDeserialize(using = GeoJsonPropertiesDeserializer.class)
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

    @JsonIgnore
    private List<String> timestampValues;

    @JsonIgnore
    private List<Double> altitudeValues;

    @JsonIgnore
    private List<Double> velocityValues;

    @JsonIgnore
    private List<Double> accuracyValues;

    @JsonIgnore
    private List<String> deviceIdValues;

    @JsonIgnore
    private List<String> sourceTypeValues;

    @JsonIgnore
    private List<Double> verticalAccuracyValues;

    @JsonIgnore
    private List<Integer> batteryValues;

    @JsonIgnore
    private List<Double> courseValues;

    public GeoJsonProperties forPointIndex(int index) {
        if (index < 0) {
            return this;
        }

        return GeoJsonProperties.builder()
                .timestamp(valueAt(timestampValues, index, timestamp))
                .altitude(valueAt(altitudeValues, index, altitude))
                .velocity(valueAt(velocityValues, index, velocity))
                .accuracy(valueAt(accuracyValues, index, accuracy))
                .deviceId(valueAt(deviceIdValues, index, deviceId))
                .sourceType(valueAt(sourceTypeValues, index, sourceType))
                .verticalAccuracy(valueAt(verticalAccuracyValues, index, verticalAccuracy))
                .battery(valueAt(batteryValues, index, battery))
                .course(valueAt(courseValues, index, course))
                .build();
    }

    private static <T> T valueAt(List<T> values, int index, T fallback) {
        if (values == null) {
            return fallback;
        }
        if (index >= values.size()) {
            return null;
        }
        return values.get(index);
    }
}
