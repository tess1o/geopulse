package org.github.tess1o.geopulse.gps.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for individual GPS points with coordinates object structure.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GpsPointDTO {
    private long id;
    private Instant timestamp;
    private CoordinatesDTO coordinates;
    private Double accuracy;
    private Double battery;
    private Double velocity;
    private Double altitude;
    private String sourceType;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoordinatesDTO {
        private double lat;
        private double lng;
    }
}