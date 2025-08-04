package org.github.tess1o.geopulse.gps.integrations.googletimeline.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Single point in a timeline path from Google Timeline
 */
@Data
public class GoogleTimelinePath {
    
    @JsonProperty("point")
    private String point; // Format: "geo:lat,lon"
    
    @JsonProperty("durationMinutesOffsetFromStartTime")
    private String durationMinutesOffsetFromStartTime;
}