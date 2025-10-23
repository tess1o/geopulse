package org.github.tess1o.geopulse.gps.integrations.googletimeline.model.semanticsegments;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Place location with latLng from new Google Timeline format
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleTimelineSemanticPlaceLocation {

    @JsonProperty("latLng")
    private String latLng; // Format: "50.0506312°, 14.3439906°"
}
