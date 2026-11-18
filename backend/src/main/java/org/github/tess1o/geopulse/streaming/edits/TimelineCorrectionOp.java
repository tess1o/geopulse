package org.github.tess1o.geopulse.streaming.edits;

/**
 * The user-facing operation of a correction. Stored corrections keep the historical {@link TimelineEditKind} names;
 * this enum is the single vocabulary of the corrections API and the history.
 */
public enum TimelineCorrectionOp {
    PLACE_STAY,
    PLACE_TRIP,
    CLEAR,
    RESIZE,
    SET_PLACE,
    SET_TRIP_TYPE,
    SET_LABEL,
    SET_ROUTE,
    MERGE;

    public static TimelineCorrectionOp of(TimelineEditKind kind) {
        return switch (kind) {
            case FORCE_STAY, SPLIT_STAY -> PLACE_STAY;
            case FORCE_TRIP -> PLACE_TRIP;
            case REMOVE_EVENT -> CLEAR;
            case SET_TIMES -> RESIZE;
            case SET_PLACE -> SET_PLACE;
            case SET_MOVEMENT_TYPE -> SET_TRIP_TYPE;
            case SET_VISIT_NAME -> SET_LABEL;
            case SET_ROUTE -> SET_ROUTE;
            case MERGE -> MERGE;
        };
    }

    public TimelineEditKind toKind() {
        return switch (this) {
            case PLACE_STAY -> TimelineEditKind.FORCE_STAY;
            case PLACE_TRIP -> TimelineEditKind.FORCE_TRIP;
            case CLEAR -> TimelineEditKind.REMOVE_EVENT;
            case RESIZE -> TimelineEditKind.SET_TIMES;
            case SET_PLACE -> TimelineEditKind.SET_PLACE;
            case SET_TRIP_TYPE -> TimelineEditKind.SET_MOVEMENT_TYPE;
            case SET_LABEL -> TimelineEditKind.SET_VISIT_NAME;
            case SET_ROUTE -> TimelineEditKind.SET_ROUTE;
            case MERGE -> TimelineEditKind.MERGE;
        };
    }

    /** Operations that change which events exist. */
    public boolean structural() {
        return switch (this) {
            case PLACE_STAY, PLACE_TRIP, CLEAR, RESIZE, MERGE -> true;
            case SET_PLACE, SET_TRIP_TYPE, SET_LABEL, SET_ROUTE -> false;
        };
    }
}
