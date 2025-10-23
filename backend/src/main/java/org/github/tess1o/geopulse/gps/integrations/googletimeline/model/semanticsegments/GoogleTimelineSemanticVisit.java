package org.github.tess1o.geopulse.gps.integrations.googletimeline.model.semanticsegments;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Visit record from new Google Timeline format
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleTimelineSemanticVisit {

    @JsonProperty("hierarchyLevel")
    private Integer hierarchyLevel;

    @JsonProperty("probability")
    private Double probability;

    @JsonProperty("topCandidate")
    private GoogleTimelineSemanticVisitCandidate topCandidate;
}
