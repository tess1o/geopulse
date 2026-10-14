package org.github.tess1o.geopulse.gps.integrations.homeassistant.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HomeAssistantLocation {
    private Double latitude;  // required; a missing value rejects the point instead of storing 0,0
    private Double longitude; // required
    private Double accuracy; // optional
    private Double altitude; // optional
    private Double speed;    // optional
}
