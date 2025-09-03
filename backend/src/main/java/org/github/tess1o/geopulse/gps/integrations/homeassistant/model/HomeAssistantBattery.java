package org.github.tess1o.geopulse.gps.integrations.homeassistant.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HomeAssistantBattery {
    private int level;
}
