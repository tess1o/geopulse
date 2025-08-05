package org.github.tess1o.geopulse.timeline.model;

/**
 * Enumeration indicating the source of timeline data.
 * Used to inform clients about data freshness and availability.
 */
public enum TimelineDataSource {
    /**
     * Timeline generated from current GPS data (today's timeline)
     */
    LIVE,
    
    /**
     * Timeline served from cached/persisted data (past days)
     */
    CACHED,
    
    /**
     * Background timeline regeneration is in progress, showing potentially stale data
     */
    REGENERATING,
    
    /**
     * Timeline combines cached past data with live current data (mixed date ranges)
     */
    MIXED
}