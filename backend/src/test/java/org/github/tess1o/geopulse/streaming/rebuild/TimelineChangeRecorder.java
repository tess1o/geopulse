package org.github.tess1o.geopulse.streaming.rebuild;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import org.github.tess1o.geopulse.streaming.events.TimelineDataChangedEvent;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Records the timeline changes announced to map matching and the weather pipeline, as they receive them. */
@ApplicationScoped
public class TimelineChangeRecorder {

    private final List<TimelineDataChangedEvent> events = new CopyOnWriteArrayList<>();

    void onTimelineChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) TimelineDataChangedEvent event) {
        events.add(event);
    }

    public List<TimelineDataChangedEvent> eventsFor(UUID userId) {
        return events.stream().filter(event -> event.getUserId().equals(userId)).toList();
    }
}
