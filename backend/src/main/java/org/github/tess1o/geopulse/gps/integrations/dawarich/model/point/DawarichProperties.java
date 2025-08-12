package org.github.tess1o.geopulse.gps.integrations.dawarich.model.point;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DawarichProperties {
    @JsonProperty("track_id")
    private String trackId;

    @JsonProperty("course_accuracy")
    private Double courseAccuracy;

    @JsonProperty("timestamp")
    private Instant timestamp; // ISO 8601 format

    @JsonProperty("altitude")
    private Double altitude;

    @JsonProperty("speed")
    private Double speed;

    @JsonProperty("horizontal_accuracy")
    private Double horizontalAccuracy;

    @JsonProperty("vertical_accuracy")
    private Double verticalAccuracy;

    @JsonProperty("device_id")
    private String deviceId;

    @JsonProperty("speed_accuracy")
    private Double speedAccuracy;

    @JsonProperty("course")
    private Double course;
}
