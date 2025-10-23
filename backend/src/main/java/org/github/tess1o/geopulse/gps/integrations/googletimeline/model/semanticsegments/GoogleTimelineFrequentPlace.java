package org.github.tess1o.geopulse.gps.integrations.googletimeline.model.semanticsegments;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Frequent place from user location profile in new Google Timeline format
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleTimelineFrequentPlace {

    @JsonProperty("placeId")
    private String placeId;

    @JsonProperty("placeLocation")
    private String placeLocation; // Format: "50.0506312°, 14.3439906°"

    @JsonProperty("label")
    private String label; // e.g., "HOME", "WORK"
}
