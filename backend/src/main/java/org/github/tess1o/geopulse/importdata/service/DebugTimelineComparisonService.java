package org.github.tess1o.geopulse.importdata.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.export.dto.TimelineDataDto;
import org.github.tess1o.geopulse.importdata.model.DebugTimelineComparison;
import org.github.tess1o.geopulse.importdata.model.DebugTimelineComparison.Event;
import org.github.tess1o.geopulse.importdata.model.DebugTimelineComparison.Row;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Compares the timeline a debug export says the user saw with the timeline regenerated from that export. Only times,
 * durations and movement types are compared: names and ids necessarily differ between the two servers.
 */
@ApplicationScoped
public class DebugTimelineComparisonService {

    private static final List<String> TYPE_ORDER = List.of("STAY", "TRIP", "GAP");

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    TimelineDataGapRepository dataGapRepository;

    /**
     * The exported timeline's events overlapping {@code [from, to]}.
     *
     * @param latestPoint the last exported GPS point; the gap after it is still open and its end is not compared
     */
    public List<Event> exportedEvents(TimelineDataDto timeline, Instant from, Instant to, Instant latestPoint) {
        List<Event> events = new ArrayList<>();
        for (TimelineDataDto.StayDto stay : emptyIfNull(timeline.getStays())) {
            if (overlaps(stay.getTimestamp(), stay.getEndTime(), from, to)) {
                events.add(new Event("STAY", stay.getTimestamp(), stay.getEndTime(), stay.getDuration(), null,
                        stay.getExplanation()));
            }
        }
        for (TimelineDataDto.TripDto trip : emptyIfNull(timeline.getTrips())) {
            if (overlaps(trip.getTimestamp(), trip.getEndTime(), from, to)) {
                events.add(new Event("TRIP", trip.getTimestamp(), trip.getEndTime(), trip.getDuration(),
                        trip.getTransportMode(), trip.getExplanation()));
            }
        }
        for (TimelineDataDto.DataGapDto gap : emptyIfNull(timeline.getDataGaps())) {
            if (overlaps(gap.getStartTime(), gap.getEndTime(), from, to)) {
                events.add(gap(gap.getStartTime(), gap.getEndTime(), gap.getDurationSeconds(), latestPoint,
                        gap.getExplanation()));
            }
        }
        return sorted(events);
    }

    /** The user's current timeline events overlapping {@code [from, to]}, in the form of {@link #exportedEvents}. */
    public List<Event> currentEvents(UUID userId, Instant from, Instant to, Instant latestPoint) {
        List<Event> events = new ArrayList<>();
        stayRepository.find("user.id = ?1 order by timestamp", userId).stream()
                .filter(stay -> overlaps(stay.getTimestamp(), stay.getTimestamp().plusSeconds(stay.getStayDuration()),
                        from, to))
                .forEach(stay -> events.add(new Event("STAY", stay.getTimestamp(),
                        stay.getTimestamp().plusSeconds(stay.getStayDuration()), stay.getStayDuration(), null,
                        stay.getExplanation())));
        tripRepository.find("user.id = ?1 order by timestamp", userId).stream()
                .filter(trip -> overlaps(trip.getTimestamp(), trip.getTimestamp().plusSeconds(trip.getTripDuration()),
                        from, to))
                .forEach(trip -> events.add(new Event("TRIP", trip.getTimestamp(),
                        trip.getTimestamp().plusSeconds(trip.getTripDuration()), trip.getTripDuration(),
                        trip.getMovementType(), trip.getExplanation())));
        dataGapRepository.find("user.id = ?1 order by startTime", userId).stream()
                .filter(gap -> overlaps(gap.getStartTime(), gap.getEndTime(), from, to))
                .forEach(gap -> events.add(gap(gap.getStartTime(), gap.getEndTime(), gap.getDurationSeconds(),
                        latestPoint, gap.getExplanation())));
        return sorted(events);
    }

    /**
     * Pairs events of both timelines that start at the same moment and lists them in time order.
     */
    public DebugTimelineComparison compare(List<Event> expected, List<Event> actual, Instant from, Instant to) {
        Map<String, Event> actualByStart = new LinkedHashMap<>();
        for (Event event : actual) {
            actualByStart.put(key(event), event);
        }

        List<Row> rows = new ArrayList<>();
        int matching = 0;
        int different = 0;
        int missing = 0;
        for (Event event : expected) {
            Event counterpart = actualByStart.remove(key(event));
            if (counterpart == null) {
                rows.add(new Row("MISSING", event, null));
                missing++;
            } else if (sameEvent(event, counterpart)) {
                rows.add(new Row("MATCH", event, counterpart));
                matching++;
            } else {
                rows.add(new Row("DIFFERENT", event, counterpart));
                different++;
            }
        }
        for (Event event : actualByStart.values()) {
            rows.add(new Row("EXTRA", null, event));
        }
        int extra = actualByStart.size();

        rows.sort(Comparator.comparing((Row row) -> eventOf(row).start())
                .thenComparing(row -> TYPE_ORDER.indexOf(eventOf(row).type())));
        boolean matches = different == 0 && missing == 0 && extra == 0;
        return new DebugTimelineComparison(true, matches, from, to, matching, different, missing, extra, rows);
    }

    private static Event gap(Instant start, Instant end, long durationSeconds, Instant latestPoint,
                             Map<String, Object> explanation) {
        boolean ongoing = end == null || start.equals(latestPoint);
        return new Event("GAP", start, ongoing ? null : end, ongoing ? null : durationSeconds, null, explanation);
    }

    private static boolean sameEvent(Event expected, Event actual) {
        return Objects.equals(expected.end(), actual.end())
                && Objects.equals(expected.durationSeconds(), actual.durationSeconds())
                && Objects.equals(expected.movementType(), actual.movementType());
    }

    private static String key(Event event) {
        return event.type() + "@" + event.start();
    }

    private static Event eventOf(Row row) {
        return row.expected() != null ? row.expected() : row.actual();
    }

    private static boolean overlaps(Instant start, Instant end, Instant from, Instant to) {
        return start != null && !start.isAfter(to) && (end == null || !end.isBefore(from));
    }

    private static List<Event> sorted(List<Event> events) {
        events.sort(Comparator.comparing(Event::start).thenComparing(event -> TYPE_ORDER.indexOf(event.type())));
        return events;
    }

    private static <T> List<T> emptyIfNull(List<T> values) {
        return values == null ? List.of() : values;
    }
}
