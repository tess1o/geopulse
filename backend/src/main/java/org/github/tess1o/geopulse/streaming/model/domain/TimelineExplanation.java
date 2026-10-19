package org.github.tess1o.geopulse.streaming.model.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Why a stay, trip or data gap starts and ends where it does, and what else the engine decided about it on the way.
 * The engine records it as it makes each decision, so it states what happened rather than a reconstruction.
 */
@Getter
@Setter
public class TimelineExplanation {

    /** Starts: FIRST_POINT, AFTER_DATA_GAP, ARRIVAL, SPARSE_STOP, LEFT_STAY, SHORT_STOP, WATER_TRANSITION and inferred ones. */
    private TimelineReason start;

    /** Decisions made while the event was open, such as a stay being confirmed or merged. */
    private List<TimelineReason> notes = new ArrayList<>();

    /** Ends: MOVED_AWAY, ARRIVAL, SPARSE_STOP, DATA_GAP, WATER_TRANSITION, LAST_POINT. */
    private TimelineReason end;

    public TimelineExplanation() {
    }

    public TimelineExplanation(TimelineReason start, List<TimelineReason> notes, TimelineReason end) {
        this.start = start;
        this.notes = notes == null ? new ArrayList<>() : new ArrayList<>(notes);
        this.end = end;
    }

    public TimelineExplanation note(TimelineReason note) {
        if (note != null) {
            notes.add(note);
        }
        return this;
    }

    public boolean isEmpty() {
        return start == null && end == null && notes.isEmpty();
    }

    public TimelineExplanation copy() {
        return new TimelineExplanation(start, notes, end);
    }

    /**
     * One explanation for several events joined into one: the first one's start, every note, and the last one's end.
     */
    public static TimelineExplanation joined(List<? extends TimelineEvent> events) {
        if (events.isEmpty()) {
            return new TimelineExplanation();
        }
        TimelineExplanation first = events.getFirst().getExplanation();
        TimelineExplanation last = events.getLast().getExplanation();
        TimelineExplanation joined = new TimelineExplanation(
                first != null ? first.getStart() : null, List.of(), last != null ? last.getEnd() : null);
        for (TimelineEvent event : events) {
            if (event.getExplanation() != null) {
                event.getExplanation().getNotes().forEach(joined::note);
            }
        }
        return joined;
    }

    /** The form stored with the event. */
    public LinkedHashMap<String, Object> toMap() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        if (start != null) {
            map.put("start", start.toMap());
        }
        if (!notes.isEmpty()) {
            map.put("notes", notes.stream().map(TimelineReason::toMap).toList());
        }
        if (end != null) {
            map.put("end", end.toMap());
        }
        return map;
    }

    /** A stored explanation with one more note. */
    public static LinkedHashMap<String, Object> withNote(Map<String, Object> stored, TimelineReason note) {
        TimelineExplanation explanation = fromMap(stored);
        if (explanation == null) {
            explanation = new TimelineExplanation();
        }
        return explanation.note(note).toMap();
    }

    public static TimelineExplanation fromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        List<TimelineReason> notes = new ArrayList<>();
        if (map.get("notes") instanceof List<?> list) {
            for (Object item : list) {
                TimelineReason reason = TimelineReason.fromMap(item);
                if (reason != null) {
                    notes.add(reason);
                }
            }
        }
        return new TimelineExplanation(TimelineReason.fromMap(map.get("start")), notes,
                TimelineReason.fromMap(map.get("end")));
    }
}
