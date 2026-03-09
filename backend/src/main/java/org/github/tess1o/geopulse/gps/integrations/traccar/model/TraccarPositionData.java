package org.github.tess1o.geopulse.gps.integrations.traccar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TraccarPositionData {
    private TraccarPosition position;
    private TraccarDevice device;
}
