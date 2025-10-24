package org.github.tess1o.geopulse.gps.integrations.googletimeline.model.semanticsegments;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Activity record from new Google Timeline format
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleTimelineSemanticActivity {

    @JsonProperty("start")
    private GoogleTimelineSemanticLatitudeLongitude start;

    @JsonProperty("end")
    private GoogleTimelineSemanticLatitudeLongitude end;

    @JsonProperty("distanceMeters")
    private Double distanceMeters;

    @JsonProperty("topCandidate")
    private GoogleTimelineSemanticActivityCandidate topCandidate;
}
