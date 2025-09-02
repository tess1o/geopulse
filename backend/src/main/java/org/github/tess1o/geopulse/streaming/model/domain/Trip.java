package org.github.tess1o.geopulse.streaming.model.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Represents a trip event - a period of movement between locations.
 * A trip is created when the user moves outside the stay radius and
 * continues until low-speed movement (indicating stopping) is detected.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trip implements TimelineEvent {

    private Instant startTime;
    private Duration duration;
    private TripType tripType;
    private List<GPSPoint> path;
    private double distanceMeters;

    @Override
    public TimelineEventType getType() {
        return TimelineEventType.TRIP;
    }

    /**
     * Get the starting location of this trip.
     *
     * @return the first GPS point in the trip path, or null if no path exists
     */
    public GPSPoint getStartLocation() {
        return (path != null && !path.isEmpty()) ? path.get(0) : null;
    }

    /**
     * Get the ending location of this trip.
     *
     * @return the last GPS point in the trip path, or null if no path exists
     */
    public GPSPoint getEndLocation() {
        return (path != null && !path.isEmpty()) ? path.get(path.size() - 1) : null;
    }
}