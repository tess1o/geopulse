package org.github.tess1o.geopulse.streaming.model.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * Represents a stay event - a period when the user was stationary at a specific location.
 * A stay is created when GPS points remain within the configured stay radius
 * for longer than the minimum stay duration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stay implements TimelineEvent {

    private Instant startTime;
    private Duration duration;
    private double latitude;
    private double longitude;

    /**
     * Optional location name if the stay location matches a favorite location
     * or has been geocoded. Will be populated during location matching phase.
     */
    private String locationName;

    /**
     * ID of matching favorite location, if any
     */
    private Long favoriteId;

    /**
     * ID of matching geocoding result, if any
     */
    private Long geocodingId;

    /** Why the engine created this event as it is; not part of the event's identity. */
    @Builder.Default
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private TimelineExplanation explanation = new TimelineExplanation();

    @Override
    public TimelineEventType getType() {
        return TimelineEventType.STAY;
    }
}