package org.github.tess1o.geopulse.gps.integrations.googletimeline.model.semanticsegments;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

/**
 * Position data from raw signals in new Google Timeline format
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleTimelinePosition {

    @JsonProperty("LatLng")
    private String latLng; // Format: "48.833657°, 2.256223°"

    @JsonProperty("accuracyMeters")
    private Integer accuracyMeters;

    @JsonProperty("altitudeMeters")
    private Double altitudeMeters;

    @JsonProperty("source")
    private String source; // e.g., "WIFI", "GPS"

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("speedMetersPerSecond")
    private Double speedMetersPerSecond;
}
