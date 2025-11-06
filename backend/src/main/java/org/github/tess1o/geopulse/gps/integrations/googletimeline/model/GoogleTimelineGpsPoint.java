package org.github.tess1o.geopulse.gps.integrations.googletimeline.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Extracted GPS point from Google Timeline data
 */
@Data
@Builder
public class GoogleTimelineGpsPoint {

    private Instant timestamp;
    private double latitude;
    private double longitude;
    private String recordType; // activity_start, activity_end, visit, timeline_point
    private String activityType;
    private double confidence;
    private Double velocityMs; // velocity in m/s, null if not available
    private Double accuracy; // accuracy in meters, null if not available
    private Double altitude; // altitude in meters, null if not available
    private int recordIndex; // index in original data
}