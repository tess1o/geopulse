package org.github.tess1o.geopulse.gps.integrations.googletimeline.model.semanticsegments;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Root structure for new Google Timeline export format (Semantic Segments)
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleTimelineSemanticSegmentsRoot {

    @JsonProperty("semanticSegments")
    private List<GoogleTimelineSemanticSegment> semanticSegments;

    @JsonProperty("rawSignals")
    private List<GoogleTimelineRawSignal> rawSignals;

    @JsonProperty("userLocationProfile")
    private GoogleTimelineUserLocationProfile userLocationProfile;
}
