package org.github.tess1o.geopulse.gps.integrations.homeassistant.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HomeAssistantLocation {
    private double latitude;
    private double longitude;
    private Double accuracy; // optional
    private Double altitude; // optional
    private Double speed;    // optional
}
