package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request payload for converting a Data Gap into a manual Stay.
 */
@Data
public class DataGapStayOverrideRequest {
    /**
     * Location strategy: LATEST_POINT or SELECTED_LOCATION.
     * Defaults to LATEST_POINT when omitted.
     */
    @Schema(examples = "SELECTED_LOCATION")
    private String locationStrategy;

    /**
     * Selected favorite location ID (SELECTED_LOCATION mode).
     */
    @Schema(examples = "12")
    private Long favoriteId;

    /**
     * Selected geocoding location ID (SELECTED_LOCATION mode).
     */
    @Schema(examples = "301")
    private Long geocodingId;

    /**
     * Custom latitude (SELECTED_LOCATION mode).
     */
    @Schema(examples = "50.4501")
    private Double latitude;

    /**
     * Custom longitude (SELECTED_LOCATION mode).
     */
    @Schema(examples = "30.5234")
    private Double longitude;

    /**
     * Optional user-defined location label for custom coordinates.
     */
    @Schema(examples = "Home")
    private String locationName;
}
