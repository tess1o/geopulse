package org.github.tess1o.geopulse.gps.integrations.googletimeline.model.semanticsegments;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Raw signal from new Google Timeline format
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleTimelineRawSignal {

    @JsonProperty("position")
    private GoogleTimelinePosition position;

    @JsonProperty("wifiScan")
    private Object wifiScan; // Not used for GPS extraction

    @JsonProperty("activityRecord")
    private Object activityRecord; // Not used for GPS extraction
}
