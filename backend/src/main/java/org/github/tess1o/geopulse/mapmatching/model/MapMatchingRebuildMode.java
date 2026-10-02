package org.github.tess1o.geopulse.mapmatching.model;

/**
 * How much of the stored map-matching history an admin-triggered re-run covers.
 */
public enum MapMatchingRebuildMode {

    /**
     * Re-queues targets that never produced a usable match (FAILED/SKIPPED) and keeps matched routes.
     */
    UNSUCCESSFUL,

    /**
     * Deletes every stored result, matched routes included, so the whole history is matched again.
     */
    ALL
}
