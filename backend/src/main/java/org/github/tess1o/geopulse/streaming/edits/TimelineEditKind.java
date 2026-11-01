package org.github.tess1o.geopulse.streaming.edits;

public enum TimelineEditKind {
    SET_MOVEMENT_TYPE,
    SET_PLACE,
    SET_VISIT_NAME,
    FORCE_STAY,
    FORCE_TRIP,
    SET_TIMES,
    SPLIT_STAY,
    MERGE,
    REMOVE_EVENT;

    public boolean structural() {
        return switch (this) {
            case SET_MOVEMENT_TYPE, SET_PLACE, SET_VISIT_NAME -> false;
            case FORCE_STAY, FORCE_TRIP, SET_TIMES, SPLIT_STAY, MERGE, REMOVE_EVENT -> true;
        };
    }
}
