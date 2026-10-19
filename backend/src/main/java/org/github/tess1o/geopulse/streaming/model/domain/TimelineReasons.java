package org.github.tess1o.geopulse.streaming.model.domain;

/**
 * Codes of {@link TimelineReason}s. The frontend has a sentence for each, so a new code needs a translation there.
 */
public final class TimelineReasons {

    // How an event starts
    /** With the first GPS point there is. */
    public static final String FIRST_POINT = "FIRST_POINT";
    /** With the first GPS point after a data gap. */
    public static final String AFTER_DATA_GAP = "AFTER_DATA_GAP";
    /** Stay: the trip before it stopped (params: stopSeconds, points, stopSpeedKmh). Trip end: the same stop. */
    public static final String ARRIVAL = "ARRIVAL";
    /**
     * Stay: the trip before it stopped, seen only because the next point came at least the minimum stay duration later
     * and within the stay radius, although the points reported a moving speed (params: gapSeconds, distanceMeters).
     * Trip end: the same stop.
     */
    public static final String SPARSE_STOP = "SPARSE_STOP";
    /** Trip: a point beyond the stay radius (params: distanceMeters, radiusMeters). */
    public static final String LEFT_STAY = "LEFT_STAY";
    /** Trip: a stop too short to be a stay (params: stopSeconds, minMinutes). */
    public static final String SHORT_STOP = "SHORT_STOP";
    /** Trip: moved between water and land (params: toWater). Also a trip end. */
    public static final String WATER_TRANSITION = "WATER_TRANSITION";
    /** Stay: in the same place on both sides of a data gap, inside a trip (params: gapSeconds, distanceMeters). */
    public static final String INFERRED_DURING_GAP = "INFERRED_DURING_GAP";
    /** Trip: travelled during a data gap (params: gapSeconds, distanceMeters). */
    public static final String INFERRED_TRIP_DURING_GAP = "INFERRED_TRIP_DURING_GAP";
    /** Trip: the only way between two stays at different places, with no trip detected (params: distanceMeters). */
    public static final String CONTINUITY = "CONTINUITY";
    /** Stay start / trip end: stopped right before a data gap and stayed there over it. */
    public static final String STOPPED_BEFORE_GAP = "STOPPED_BEFORE_GAP";
    /** Data gap: no points for longer than the gap threshold (params: gapSeconds, thresholdSeconds). */
    public static final String NO_POINTS = "NO_POINTS";
    /** Data gap: still open, no points since the last one (params: thresholdSeconds). */
    public static final String NO_POINTS_SINCE_LAST = "NO_POINTS_SINCE_LAST";

    // Decisions while an event is open
    /** Stay: long enough within the radius to be a stay (params: radiusMeters, minMinutes). */
    public static final String CONFIRMED = "CONFIRMED";
    /** Stay: points outside the radius kept because they are in the same favorite area. */
    public static final String FAVORITE_AREA = "FAVORITE_AREA";
    /** Stay: continued over a data gap because the points on both sides are in the same place (params: gapSeconds). */
    public static final String CONTINUED_OVER_GAP = "CONTINUED_OVER_GAP";
    /** Stay: several stays at the same place merged into one (params: stays). */
    public static final String MERGED = "MERGED";
    /** Trip: several trip segments joined into one (params: segments). */
    public static final String JOINED_SEGMENTS = "JOINED_SEGMENTS";

    /** Created or changed by the user's manual Trip -> Stay -> Trip split. */
    public static final String MANUAL_SPLIT = "MANUAL_SPLIT";
    /** Created or extended by the user's manual Data Gap -> Stay conversion. */
    public static final String MANUAL_GAP_STAY = "MANUAL_GAP_STAY";

    // How an event ends
    /** Stay: a point beyond the stay radius (params: distanceMeters, radiusMeters). */
    public static final String MOVED_AWAY = "MOVED_AWAY";
    /** At a data gap (params: gapSeconds). */
    public static final String DATA_GAP = "DATA_GAP";
    /** At the last GPS point there is; the event may still be going on. */
    public static final String LAST_POINT = "LAST_POINT";

    private TimelineReasons() {
    }
}
