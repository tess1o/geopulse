package org.github.tess1o.geopulse.gps.integrations.homeassistant.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HomeAssistantGpsData {
    @JsonProperty("device_id")
    private String deviceId;
    private Instant timestamp;
    private HomeAssistantLocation location;
    private HomeAssistantBattery battery;
}
