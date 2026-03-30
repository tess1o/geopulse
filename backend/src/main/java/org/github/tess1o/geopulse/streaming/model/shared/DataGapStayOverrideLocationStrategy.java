package org.github.tess1o.geopulse.streaming.model.shared;

/**
 * Strategy used for manual Data Gap -> Stay conversion location selection.
 */
public enum DataGapStayOverrideLocationStrategy {
    /**
     * Use the latest known GPS point before gap start and resolve its location.
     */
    LATEST_POINT,

    /**
     * Use a user-selected location (favorite, geocoding, or custom coordinates).
     */
    SELECTED_LOCATION
}
