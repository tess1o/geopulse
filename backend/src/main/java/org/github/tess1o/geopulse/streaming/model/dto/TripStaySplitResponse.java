package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record TripStaySplitResponse(
        Long overrideId,
        Long originalTripId,
        Long firstTripId,
        Long stayId,
        Long secondTripId,
        Instant stayStartTime,
        Instant stayEndTime,
        String locationName,
        Instant regenerationStartTime,
        TimelineTripDTO firstTrip,
        TimelineStayLocationDTO stay,
        TimelineTripDTO secondTrip,
        /** The job regenerating the timeline after a split was undone, to follow; null when none could start now. */
        UUID timelineJobId,
        /** Whether the regeneration was queued behind one that is already running (then timelineJobId is null). */
        boolean timelineJobQueued
) {
    public TripStaySplitResponse withTimelineJob(UUID jobId, boolean queued) {
        return new TripStaySplitResponse(overrideId, originalTripId, firstTripId, stayId, secondTripId, stayStartTime,
                stayEndTime, locationName, regenerationStartTime, firstTrip, stay, secondTrip, jobId, queued);
    }
}
