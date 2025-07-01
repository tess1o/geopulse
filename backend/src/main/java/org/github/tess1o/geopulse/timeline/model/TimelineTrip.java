package org.github.tess1o.geopulse.timeline.model;

import org.github.tess1o.geopulse.shared.geo.GpsPoint;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record TimelineTrip(Instant startTime, Instant endTime, List<? extends GpsPoint> path, TravelMode travelMode) {
    public Duration getDuration() {
        return Duration.between(startTime, endTime);
    }
}