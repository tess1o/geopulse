package org.github.tess1o.geopulse.timeline.model;

import java.time.Duration;
import java.time.Instant;

public record TimelineStayPoint(double longitude, double latitude, Instant startTime, Instant endTime, Duration duration) {
}

