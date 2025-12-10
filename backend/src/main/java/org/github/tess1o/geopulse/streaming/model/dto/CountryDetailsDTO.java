package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO containing comprehensive information about a country.
 * Includes statistics, cities breakdown, and top places.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CountryDetailsDTO {
    private String countryName;

    // Aggregated statistics for this country
    private LocationStatisticsDTO statistics;

    // Cities in this country (sorted by visit count)
    private List<CityInCountryDTO> cities;

    // Top places across all cities in this country (sorted by visit count)
    private List<TopPlaceInLocationDTO> topPlaces;
}
