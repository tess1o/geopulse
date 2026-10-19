package org.github.tess1o.geopulse.streaming.service.explanation;

import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineExplanation;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReason;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReasons;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEventExplanationDetailsDTO.Coordinates;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEventExplanationDetailsDTO.Filtered;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEventExplanationDetailsDTO.Point;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEventExplanationDetailsDTO.SettingChange;
import org.github.tess1o.geopulse.streaming.util.TimelineGpsAccuracyFilter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Marks the stored GPS points around a stay, trip or data gap the way the timeline engine saw them: which it left out
 * for their accuracy, and which made each decision recorded in the event's explanation. It works from the points, the
 * event's times and the explanation's values, without running the engine again:
 * <ul>
 *   <li>the engine skips points less accurate than the GPS accuracy threshold before anything else;</li>
 *   <li>a stay found by {@code ARRIVAL} starts at the first of its {@code points} stopped points;</li>
 *   <li>a stay ends with its last point, and the next used point is the one that moved away ({@code MOVED_AWAY}),
 *   which also starts the next trip ({@code LEFT_STAY});</li>
 *   <li>the stay centre is the average of the stay's used points, as the engine measures the radius from it.</li>
 * </ul>
 */
public final class TimelineExplanationPointClassifier {

    /** Same tolerance the engine allows over the stay radius. */
    private static final double DISTANCE_EPSILON_METERS = 0.5;

    public enum Kind { STAY, TRIP, DATA_GAP }

    public enum Part { BEFORE, EVENT, AFTER }

    /** A stored GPS point. Accuracy is null when the source did not send one. */
    public record RawPoint(Instant timestamp, double latitude, double longitude, Double accuracyMeters,
                           Double speedKmh) {
    }

    public record Result(List<Point> points, Coordinates centre, Filtered filtered) {
    }

    private TimelineExplanationPointClassifier() {
    }

    private static final class Entry {
        final RawPoint point;
        final Part part;
        final boolean used;
        final Set<String> reasons = new LinkedHashSet<>();
        Double distanceFromCentre;
        Boolean outsideRadius;

        Entry(RawPoint point, Part part, boolean used) {
            this.point = point;
            this.part = part;
            this.used = used;
        }

        Instant time() {
            return point.timestamp();
        }
    }

    /**
     * @param points         the stored points of the event and around it, oldest first
     * @param storedLocation the stay's stored location; its centre when its own points can't give one
     */
    public static Result classify(Kind kind, Instant start, Instant end, TimelineExplanation explanation,
                                  TimelineConfig config, List<RawPoint> points, Coordinates storedLocation) {
        TimelineExplanation reasons = explanation != null ? explanation : new TimelineExplanation();
        List<Entry> entries = new ArrayList<>(points.size());
        for (RawPoint point : points) {
            entries.add(new Entry(point, part(kind, start, end, point.timestamp()),
                    TimelineGpsAccuracyFilter.shouldIncludeAccuracy(point.accuracyMeters(), config)));
        }

        Coordinates centre = null;
        if (kind == Kind.STAY) {
            centre = stayCentre(entries, reasons, storedLocation);
            if (centre != null && config.getStaypointRadiusMeters() != null) {
                double radius = config.getStaypointRadiusMeters();
                for (Entry entry : entries) {
                    double distance = GeoUtils.haversine(centre.latitude(), centre.longitude(),
                            entry.point.latitude(), entry.point.longitude());
                    entry.distanceFromCentre = (double) Math.round(distance);
                    entry.outsideRadius = distance > radius + DISTANCE_EPSILON_METERS;
                }
            }
        }

        switch (kind) {
            case STAY -> markStay(entries, start, end, reasons, config);
            case TRIP -> markTrip(entries, start, end, reasons);
            case DATA_GAP -> markDataGap(entries, start, end, reasons);
        }

        int filteredInEvent = 0;
        int filteredInContext = 0;
        List<Point> result = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            if (!entry.used) {
                if (entry.part == Part.EVENT) {
                    filteredInEvent++;
                } else {
                    filteredInContext++;
                }
            }
            RawPoint p = entry.point;
            result.add(new Point(p.timestamp(), p.latitude(), p.longitude(), p.accuracyMeters(), p.speedKmh(),
                    entry.part.name(), entry.used, entry.distanceFromCentre, entry.outsideRadius,
                    List.copyOf(entry.reasons)));
        }
        return new Result(result, centre,
                new Filtered(filteredInEvent, filteredInContext,
                        TimelineGpsAccuracyFilter.getActiveMaxAccuracyThreshold(config)));
    }

    /**
     * Settings whose value in the explanation (the value the engine used) differs from the current one.
     */
    public static List<SettingChange> settingsChanged(TimelineExplanation explanation, TimelineConfig config) {
        List<SettingChange> changes = new ArrayList<>();
        if (explanation == null) {
            return changes;
        }
        List<TimelineReason> all = new ArrayList<>();
        if (explanation.getStart() != null) all.add(explanation.getStart());
        all.addAll(explanation.getNotes());
        if (explanation.getEnd() != null) all.add(explanation.getEnd());

        Set<String> seen = new LinkedHashSet<>();
        for (TimelineReason reason : all) {
            switch (reason.code()) {
                case TimelineReasons.CONFIRMED, TimelineReasons.MOVED_AWAY, TimelineReasons.LEFT_STAY ->
                        compare(changes, seen, "staypointRadiusMeters", reason.params().get("radiusMeters"),
                                config.getStaypointRadiusMeters());
                default -> {
                }
            }
            switch (reason.code()) {
                case TimelineReasons.CONFIRMED, TimelineReasons.SHORT_STOP ->
                        compare(changes, seen, "staypointMinDurationMinutes", reason.params().get("minMinutes"),
                                config.getStaypointMinDurationMinutes());
                case TimelineReasons.NO_POINTS, TimelineReasons.NO_POINTS_SINCE_LAST ->
                        compare(changes, seen, "dataGapThresholdSeconds", reason.params().get("thresholdSeconds"),
                                config.getDataGapThresholdSeconds());
                default -> {
                }
            }
        }
        return changes;
    }

    private static void compare(List<SettingChange> changes, Set<String> seen, String setting, Object builtWith,
                                Number current) {
        if (!(builtWith instanceof Number built) || current == null || seen.contains(setting)) {
            return;
        }
        if (Math.round(built.doubleValue()) != Math.round(current.doubleValue())) {
            seen.add(setting);
            changes.add(new SettingChange(setting, built, current));
        }
    }

    private static Part part(Kind kind, Instant start, Instant end, Instant time) {
        // A data gap runs from the last point before it to the first point after it: those two are its edges, not
        // points inside it
        boolean beforeEvent = kind == Kind.DATA_GAP ? !time.isAfter(start) : time.isBefore(start);
        boolean afterEvent = kind == Kind.DATA_GAP ? !time.isBefore(end) : time.isAfter(end);
        if (beforeEvent) return Part.BEFORE;
        if (afterEvent) return Part.AFTER;
        return Part.EVENT;
    }

    private static Coordinates stayCentre(List<Entry> entries, TimelineExplanation explanation,
                                          Coordinates storedLocation) {
        // A merged stay also covers the trips between the stays it was merged from, so its points don't give the
        // centre the engine used
        if (hasNote(explanation, TimelineReasons.MERGED)) {
            return storedLocation;
        }
        double latitude = 0;
        double longitude = 0;
        int count = 0;
        for (Entry entry : entries) {
            if (entry.part == Part.EVENT && entry.used) {
                latitude += entry.point.latitude();
                longitude += entry.point.longitude();
                count++;
            }
        }
        return count == 0 ? storedLocation : new Coordinates(latitude / count, longitude / count);
    }

    private static void markStay(List<Entry> entries, Instant start, Instant end, TimelineExplanation explanation,
                                 TimelineConfig config) {
        List<Entry> used = eventUsed(entries);
        TimelineReason startReason = explanation.getStart();
        if (startReason != null) {
            switch (startReason.code()) {
                case TimelineReasons.ARRIVAL -> markFirst(used, intParam(startReason, "points", 1),
                        TimelineReasons.ARRIVAL);
                case TimelineReasons.INFERRED_DURING_GAP -> markGapEdges(entries, start, end, startReason.code());
                case TimelineReasons.MANUAL_SPLIT, TimelineReasons.MANUAL_GAP_STAY -> {
                }
                default -> markFirst(used, 1, startReason.code());
            }
        }

        for (TimelineReason note : explanation.getNotes()) {
            switch (note.code()) {
                case TimelineReasons.CONFIRMED -> {
                    Integer minMinutes = integerParam(note, "minMinutes", config.getStaypointMinDurationMinutes());
                    if (minMinutes != null) {
                        Instant confirmedAt = start.plusSeconds(minMinutes * 60L);
                        used.stream().filter(entry -> !entry.time().isBefore(confirmedAt)).findFirst()
                                .ifPresent(entry -> entry.reasons.add(TimelineReasons.CONFIRMED));
                    }
                }
                case TimelineReasons.FAVORITE_AREA -> used.stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.outsideRadius))
                        .forEach(entry -> entry.reasons.add(TimelineReasons.FAVORITE_AREA));
                case TimelineReasons.CONTINUED_OVER_GAP -> markLongestGap(used, note.code());
                default -> {
                }
            }
        }

        TimelineReason endReason = explanation.getEnd();
        if (endReason != null) {
            switch (endReason.code()) {
                case TimelineReasons.MOVED_AWAY -> markFirstAfter(entries, end, TimelineReasons.MOVED_AWAY);
                case TimelineReasons.DATA_GAP -> {
                    markLast(used, TimelineReasons.DATA_GAP);
                    markFirstAfter(entries, end, TimelineReasons.DATA_GAP);
                }
                default -> markLast(used, endReason.code());
            }
        }
    }

    private static void markTrip(List<Entry> entries, Instant start, Instant end, TimelineExplanation explanation) {
        List<Entry> used = eventUsed(entries);
        TimelineReason startReason = explanation.getStart();
        if (startReason != null) {
            switch (startReason.code()) {
                case TimelineReasons.SHORT_STOP -> {
                    Long stopSeconds = longParam(startReason, "stopSeconds");
                    Instant stopEnd = start.plusSeconds(stopSeconds != null ? stopSeconds : 0);
                    used.stream().filter(entry -> !entry.time().isAfter(stopEnd))
                            .forEach(entry -> entry.reasons.add(TimelineReasons.SHORT_STOP));
                }
                case TimelineReasons.INFERRED_TRIP_DURING_GAP -> markGapEdges(entries, start, end, startReason.code());
                case TimelineReasons.CONTINUITY, TimelineReasons.MANUAL_SPLIT -> {
                }
                default -> markFirst(used, 1, startReason.code());
            }
        }

        TimelineReason endReason = explanation.getEnd();
        if (endReason != null) {
            switch (endReason.code()) {
                case TimelineReasons.ARRIVAL -> {
                    // The trip ends at the first stopped point; the rest of the stop belongs to the stay after it
                    markLast(used, TimelineReasons.ARRIVAL);
                    int remaining = intParam(endReason, "points", 1) - 1;
                    entries.stream().filter(entry -> entry.part == Part.AFTER && entry.used).limit(Math.max(0, remaining))
                            .forEach(entry -> entry.reasons.add(TimelineReasons.ARRIVAL));
                }
                case TimelineReasons.DATA_GAP -> {
                    markLast(used, TimelineReasons.DATA_GAP);
                    markFirstAfter(entries, end, TimelineReasons.DATA_GAP);
                }
                default -> markLast(used, endReason.code());
            }
        }
    }

    private static void markDataGap(List<Entry> entries, Instant start, Instant end, TimelineExplanation explanation) {
        TimelineReason startReason = explanation.getStart();
        String code = startReason != null ? startReason.code() : TimelineReasons.NO_POINTS;
        if (TimelineReasons.NO_POINTS_SINCE_LAST.equals(code)) {
            lastUsed(entries, entry -> !entry.time().isAfter(start)).ifPresent(entry -> entry.reasons.add(code));
        } else {
            markGapEdges(entries, start, end, code);
        }
    }

    private static void markGapEdges(List<Entry> entries, Instant start, Instant end, String code) {
        lastUsed(entries, entry -> !entry.time().isAfter(start)).ifPresent(entry -> entry.reasons.add(code));
        entries.stream().filter(entry -> entry.used && !entry.time().isBefore(end)).findFirst()
                .ifPresent(entry -> entry.reasons.add(code));
    }

    private static void markLongestGap(List<Entry> used, String code) {
        int longestAt = -1;
        long longest = 0;
        for (int i = 1; i < used.size(); i++) {
            long seconds = used.get(i).time().getEpochSecond() - used.get(i - 1).time().getEpochSecond();
            if (seconds > longest) {
                longest = seconds;
                longestAt = i;
            }
        }
        if (longestAt > 0) {
            used.get(longestAt - 1).reasons.add(code);
            used.get(longestAt).reasons.add(code);
        }
    }

    private static List<Entry> eventUsed(List<Entry> entries) {
        return entries.stream().filter(entry -> entry.part == Part.EVENT && entry.used).toList();
    }

    private static void markFirst(List<Entry> used, int count, String code) {
        used.stream().limit(Math.max(1, count)).forEach(entry -> entry.reasons.add(code));
    }

    private static void markLast(List<Entry> used, String code) {
        if (!used.isEmpty()) {
            used.getLast().reasons.add(code);
        }
    }

    private static void markFirstAfter(List<Entry> entries, Instant end, String code) {
        entries.stream().filter(entry -> entry.used && entry.time().isAfter(end)).findFirst()
                .ifPresent(entry -> entry.reasons.add(code));
    }

    private static Optional<Entry> lastUsed(List<Entry> entries, Predicate<Entry> filter) {
        Entry last = null;
        for (Entry entry : entries) {
            if (entry.used && filter.test(entry)) {
                last = entry;
            }
        }
        return Optional.ofNullable(last);
    }

    private static boolean hasNote(TimelineExplanation explanation, String code) {
        return explanation.getNotes().stream().anyMatch(note -> Objects.equals(note.code(), code));
    }

    private static Integer integerParam(TimelineReason reason, String name, Integer fallback) {
        Object value = reason.params().get(name);
        return value instanceof Number number ? Integer.valueOf(number.intValue()) : fallback;
    }

    private static int intParam(TimelineReason reason, String name, int fallback) {
        Object value = reason.params().get(name);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static Long longParam(TimelineReason reason, String name) {
        Map<String, Object> params = reason.params();
        return params.get(name) instanceof Number number ? number.longValue() : null;
    }
}
