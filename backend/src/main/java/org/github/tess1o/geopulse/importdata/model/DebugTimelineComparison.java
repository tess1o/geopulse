package org.github.tess1o.geopulse.importdata.model;

import java.time.Instant;
import java.util.List;

/**
 * The timeline a debug export says the user saw, compared with the one regenerated from it, within the range the user
 * asked to export.
 *
 * @param available false when the export has no timeline to compare with
 * @param matches   true when every event is the same
 * @param rows      events of both timelines in time order, paired where they start at the same moment
 */
public record DebugTimelineComparison(boolean available,
                                      boolean matches,
                                      Instant from,
                                      Instant to,
                                      int matching,
                                      int different,
                                      int missing,
                                      int extra,
                                      List<Row> rows) {

    public static DebugTimelineComparison unavailable(Instant from, Instant to) {
        return new DebugTimelineComparison(false, false, from, to, 0, 0, 0, 0, List.of());
    }

    /**
     * One stay, trip or data gap.
     *
     * @param type         STAY, TRIP or GAP
     * @param end          null for the data gap that is still open after the last point
     * @param movementType trips only
     * @param explanation  why the timeline engine created the event as it is, if known; not compared
     */
    public record Event(String type, Instant start, Instant end, Long durationSeconds, String movementType,
                        java.util.Map<String, Object> explanation) {

        /** A readable one-line form, also used by tests. */
        public String describe() {
            return switch (type) {
                case "STAY" -> "STAY " + start + " " + durationSeconds + "s";
                case "TRIP" -> "TRIP " + start + " " + durationSeconds + "s " + movementType;
                default -> "GAP " + start + " .. " + (end == null ? "ongoing" : end);
            };
        }
    }

    /**
     * @param status   MATCH, DIFFERENT (same kind of event starting at the same moment, but not the same), MISSING
     *                 (only in the exported timeline) or EXTRA (only in the regenerated one)
     * @param expected the event in the exported timeline, if any
     * @param actual   the event in the regenerated timeline, if any
     */
    public record Row(String status, Event expected, Event actual) {
    }
}
