package org.github.tess1o.geopulse.gps.integrations.googletimeline.model;

/**
 * Types of Google Timeline records
 */
public enum GoogleTimelineRecordType {
    ACTIVITY,      // Movement/transport activity
    VISIT,         // Stay at a location
    TIMELINE_PATH, // Detailed path with multiple GPS points
    UNKNOWN        // Unrecognized record type
}