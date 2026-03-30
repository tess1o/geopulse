package org.github.tess1o.geopulse.gpssource.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OwnTracksMqttConfigDTO {
    private boolean mqttEnabled;
    private String brokerHost;
    private int brokerPort;
    private boolean tlsEnabled;
}
