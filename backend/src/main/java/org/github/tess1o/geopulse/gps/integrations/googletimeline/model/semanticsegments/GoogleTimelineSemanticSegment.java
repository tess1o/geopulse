package org.github.tess1o.geopulse.gps.integrations.googletimeline.model.semanticsegments;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Semantic segment from new Google Timeline format
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleTimelineSemanticSegment {

    @JsonProperty("startTime")
    private Instant startTime;

    @JsonProperty("endTime")
    private Instant endTime;

    @JsonProperty("startTimeTimezoneUtcOffsetMinutes")
    private Integer startTimeTimezoneUtcOffsetMinutes;

    @JsonProperty("endTimeTimezoneUtcOffsetMinutes")
    private Integer endTimeTimezoneUtcOffsetMinutes;

    @JsonProperty("visit")
    private GoogleTimelineSemanticVisit visit;

    @JsonProperty("activity")
    private GoogleTimelineSemanticActivity activity;

    @JsonProperty("timelinePath")
    private List<GoogleTimelineSemanticTimelinePath> timelinePath;

    /**
     * Check if this segment has valid timestamp data
     */
    public boolean hasValidTimes() {
        return startTime != null && endTime != null && !startTime.isAfter(endTime);
    }
}
