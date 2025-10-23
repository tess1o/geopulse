package org.github.tess1o.geopulse.gps.integrations.googletimeline.model.semanticsegments;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Visit candidate with place location from new Google Timeline format
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleTimelineSemanticVisitCandidate {

    @JsonProperty("placeId")
    private String placeId;

    @JsonProperty("semanticType")
    private String semanticType;

    @JsonProperty("probability")
    private Double probability;

    @JsonProperty("placeLocation")
    private GoogleTimelineSemanticPlaceLocation placeLocation;
}
