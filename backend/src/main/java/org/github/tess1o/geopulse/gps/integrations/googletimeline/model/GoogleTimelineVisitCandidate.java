package org.github.tess1o.geopulse.gps.integrations.googletimeline.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Top candidate for visit location from Google Timeline
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleTimelineVisitCandidate {
    
    @JsonProperty("probability")
    private String probability;
    
    @JsonProperty("semanticType")
    private String semanticType;
    
    @JsonProperty("placeID")
    private String placeID;
    
    @JsonProperty("placeLocation")
    private String placeLocation; // Format: "geo:lat,lon"
}