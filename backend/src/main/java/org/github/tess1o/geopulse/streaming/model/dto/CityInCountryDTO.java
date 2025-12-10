package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a city within a country.
 * Used in country details to show breakdown of cities.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CityInCountryDTO {
    private String cityName;
    private long visitCount;
    private long totalDuration;         // Total duration in seconds
    private int uniquePlaces;           // Number of distinct places in this city
}
