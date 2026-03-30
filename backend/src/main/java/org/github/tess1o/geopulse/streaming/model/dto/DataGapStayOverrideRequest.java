package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.Data;

/**
 * Request payload for converting a Data Gap into a manual Stay.
 */
@Data
public class DataGapStayOverrideRequest {
    /**
     * Location strategy: LATEST_POINT or SELECTED_LOCATION.
     * Defaults to LATEST_POINT when omitted.
     */
    private String locationStrategy;

    /**
     * Selected favorite location ID (SELECTED_LOCATION mode).
     */
    private Long favoriteId;

    /**
     * Selected geocoding location ID (SELECTED_LOCATION mode).
     */
    private Long geocodingId;

    /**
     * Custom latitude (SELECTED_LOCATION mode).
     */
    private Double latitude;

    /**
     * Custom longitude (SELECTED_LOCATION mode).
     */
    private Double longitude;

    /**
     * Optional user-defined location label for custom coordinates.
     */
    private String locationName;
}
