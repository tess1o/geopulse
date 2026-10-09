package org.github.tess1o.geopulse.gps.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.geocoding.dto.LocationTimezoneDTO;

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

    /**
     * Local timezone of the point. Only set when the client requests location timezones.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocationTimezoneDTO locationTimezone;

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

    public GpsPointDTO(long id,
                       Instant timestamp,
                       CoordinatesDTO coordinates,
                       Double accuracy,
                       Double battery,
                       Double velocity,
                       Double altitude,
                       String sourceType,
                       List<GpsTelemetryDisplayDTO> telemetryGpsData,
                       List<GpsTelemetryDisplayDTO> telemetryCurrentPopup) {
        this(id, timestamp, coordinates, accuracy, battery, velocity, altitude, sourceType,
                telemetryGpsData, telemetryCurrentPopup, null);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoordinatesDTO {
        private double lat;
        private double lng;
    }
}