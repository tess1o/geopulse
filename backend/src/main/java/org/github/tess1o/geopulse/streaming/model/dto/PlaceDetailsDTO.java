package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO containing comprehensive information about a specific place.
 * Includes place metadata and aggregated statistics.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceDetailsDTO {
    // Place identification
    private String type;                // "favorite" or "geocoding"
    private Long id;                    // favoriteId or geocodingId
    private String locationName;        // Display name of the place
    private boolean canEdit;            // Whether the user can edit this place name

    // Geometry (point or area)
    private PlaceGeometryDTO geometry;

    // Statistics
    private PlaceStatisticsDTO statistics;

    // Related favorite (for geocoding points with no visits due to favorite priority)
    private FavoriteRelationDTO relatedFavorite;
}
