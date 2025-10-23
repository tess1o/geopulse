package org.github.tess1o.geopulse.gps.integrations.googletimeline.model.semanticsegments;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

/**
 * Timeline path point from new Google Timeline format
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleTimelineSemanticTimelinePath {

    @JsonProperty("point")
    private String point; // Format: "50.0506312°, 14.3439906°"

    @JsonProperty("time")
    private Instant time; // Absolute timestamp instead of offset
}
