package org.github.tess1o.geopulse.streaming.model;

/** What started a timeline job. */
public enum TimelineJobType {
    /** The user regenerated the whole timeline. */
    FULL_REBUILD,
    /** Regeneration from a point in time, after GPS data changed there. */
    PARTIAL_REBUILD,
    /** Rebuild of what saved, undone or restored corrections changed. */
    CORRECTION,
    /** Regeneration after an import. */
    IMPORT,
    /** Regeneration campaign started by an administrator or an upgrade. */
    CAMPAIGN,
    /** A caller that does not say. */
    OTHER
}
