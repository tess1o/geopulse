package org.github.tess1o.geopulse.gps.integrations.googletimeline.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Represents an activity record from Google Timeline (movement/transport)
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleTimelineActivity {
    
    @JsonProperty("probability")
    private String probability;
    
    @JsonProperty("start")
    private String start; // Format: "geo:lat,lon"
    
    @JsonProperty("end") 
    private String end; // Format: "geo:lat,lon"
    
    @JsonProperty("distanceMeters")
    private String distanceMeters;
    
    @JsonProperty("topCandidate")
    private GoogleTimelineActivityCandidate topCandidate;
}