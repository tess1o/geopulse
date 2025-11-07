package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a related favorite location for a geocoding point.
 * Used when a geocoding location has no visits because a nearby favorite takes priority.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FavoriteRelationDTO {

    /**
     * ID of the related favorite location
     */
    private Long id;

    /**
     * Name of the related favorite location
     */
    private String name;

    /**
     * Distance in meters from the geocoding point to the favorite
     */
    private Double distanceMeters;

    /**
     * Reason for the relation: "nearby" for point favorites within threshold,
     * "contains_point" for area favorites that contain the point
     */
    private String reason;

    /**
     * Total number of visits to the related favorite location
     */
    private Long totalVisits;
}
