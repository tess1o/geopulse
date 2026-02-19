package org.github.tess1o.geopulse.statistics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal DTO used by StatisticsRepository to return heatmap location data.
 * Converted to HeatmapDataPoint for the REST response.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HeatmapPlace {
    private double latitude;
    private double longitude;
    private long durationSeconds;
    private long visits;
    private String name;
}
