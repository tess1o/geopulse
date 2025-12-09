package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for city summary information in lists/dashboards.
 * Contains basic metrics without full statistics.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CitySummaryDTO {
    private String cityName;
    private String country;
    private long visitCount;
    private long totalDuration;         // Total duration in seconds
    private int uniquePlaces;           // Number of distinct places in this city
}
