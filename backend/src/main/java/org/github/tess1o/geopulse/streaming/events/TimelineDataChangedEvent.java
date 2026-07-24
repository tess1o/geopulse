package org.github.tess1o.geopulse.streaming.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Event fired after timeline stays/trips/data gaps have been regenerated.
 */
@Getter
@AllArgsConstructor
@ToString
public class TimelineDataChangedEvent {

    private final UUID userId;
    private final Instant affectedFrom;
    private final Instant affectedTo;
    private final UUID jobId;
}
