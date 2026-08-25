package org.github.tess1o.geopulse.mapmatching.model;

public enum MapMatchingSource {
    ON_DEMAND(100),
    AUTOMATIC(50),
    HISTORICAL(10);

    private final int priority;

    MapMatchingSource(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
