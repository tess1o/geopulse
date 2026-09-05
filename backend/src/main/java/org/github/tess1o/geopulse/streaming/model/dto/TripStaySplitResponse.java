package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.Builder;

import java.time.Instant;

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
        TimelineTripDTO secondTrip
) {
}
