package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO containing comprehensive information about a city.
 * Includes statistics, geometry, and top places within the city.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CityDetailsDTO {
    private String cityName;
    private String country;

    // Geometry (centroid of all visits in this city)
    private PlaceGeometryDTO geometry;

    // Aggregated statistics for this city
    private LocationStatisticsDTO statistics;

    // Top places in this city (sorted by visit count)
    private List<TopPlaceInLocationDTO> topPlaces;
}
