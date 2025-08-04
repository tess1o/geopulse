package org.github.tess1o.geopulse.gps.integrations.googletimeline.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Represents a visit record from Google Timeline (stay at location)
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleTimelineVisit {
    
    @JsonProperty("hierarchyLevel")
    private String hierarchyLevel;
    
    @JsonProperty("probability")
    private String probability;
    
    @JsonProperty("topCandidate")
    private GoogleTimelineVisitCandidate topCandidate;
}