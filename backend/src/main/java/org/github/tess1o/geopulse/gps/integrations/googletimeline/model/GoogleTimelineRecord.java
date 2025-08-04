package org.github.tess1o.geopulse.gps.integrations.googletimeline.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

/**
 * Base record for Google Timeline JSON data.
 * Represents a timeline entry that can be an activity, visit, or timeline path.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleTimelineRecord {
    
    @JsonProperty("startTime")
    private Instant startTime;
    
    @JsonProperty("endTime")
    private Instant endTime;
    
    @JsonProperty("activity")
    private GoogleTimelineActivity activity;
    
    @JsonProperty("visit")
    private GoogleTimelineVisit visit;
    
    @JsonProperty("timelinePath")
    private GoogleTimelinePath[] timelinePath;
    
    /**
     * Determine the type of this record
     */
    public GoogleTimelineRecordType getRecordType() {
        if (activity != null) {
            return GoogleTimelineRecordType.ACTIVITY;
        } else if (visit != null) {
            return GoogleTimelineRecordType.VISIT;
        } else if (timelinePath != null && timelinePath.length > 0) {
            return GoogleTimelineRecordType.TIMELINE_PATH;
        }
        return GoogleTimelineRecordType.UNKNOWN;
    }
    
    /**
     * Check if this record has valid timestamp data
     */
    public boolean hasValidTimes() {
        return startTime != null && endTime != null && !startTime.isAfter(endTime);
    }
}