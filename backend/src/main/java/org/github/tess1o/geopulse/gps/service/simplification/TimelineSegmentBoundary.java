package org.github.tess1o.geopulse.gps.service.simplification;

import java.time.Instant;

/**
 * Lightweight DTO representing a timeline segment (trip or stay) boundary.
 * Used for segment-aware GPS path simplification.
 */
public record TimelineSegmentBoundary(
    Instant startTime,
    Long durationSeconds,
    SegmentType type
) {
    /**
     * Calculate the end time of this segment.
     * @return The end timestamp (startTime + durationSeconds)
     */
    public Instant getEndTime() {
        return startTime.plusSeconds(durationSeconds);
    }

    /**
     * Type of timeline segment.
     */
    public enum SegmentType {
        TRIP,
        STAY
    }
}
