package org.github.tess1o.geopulse.gps.integrations.overland.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class Properties {
    private double speed;
    @JsonProperty("battery_state")
    private String batteryState;
    @JsonProperty("battery_level")
    private double batteryLevel;
    private List<Object> motion; // Empty array in your example
    private Instant timestamp;
    @JsonProperty("speed_accuracy")
    private double speedAccuracy;
    @JsonProperty("horizontal_accuracy")
    private double horizontalAccuracy;
    @JsonProperty("vertical_accuracy")
    private double verticalAccuracy;
    private String wifi;
    private int course;
    private int altitude;
    @JsonProperty("course_accuracy")
    private double courseAccuracy;
    @JsonProperty("device_id")
    private String deviceId;
}