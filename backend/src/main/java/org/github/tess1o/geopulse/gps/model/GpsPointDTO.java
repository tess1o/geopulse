package org.github.tess1o.geopulse.gps.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

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
    private List<GpsTelemetryDisplayDTO> telemetryGpsData;
    private List<GpsTelemetryDisplayDTO> telemetryCurrentPopup;

    public GpsPointDTO(long id,
                       Instant timestamp,
                       CoordinatesDTO coordinates,
                       Double accuracy,
                       Double battery,
                       Double velocity,
                       Double altitude,
                       String sourceType) {
        this(id, timestamp, coordinates, accuracy, battery, velocity, altitude, sourceType, null, null);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoordinatesDTO {
        private double lat;
        private double lng;
    }
}