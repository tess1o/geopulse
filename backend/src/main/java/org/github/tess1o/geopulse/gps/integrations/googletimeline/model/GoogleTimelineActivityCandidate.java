package org.github.tess1o.geopulse.gps.integrations.googletimeline.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Top candidate for activity type from Google Timeline
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleTimelineActivityCandidate {
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("probability")
    private String probability;
}