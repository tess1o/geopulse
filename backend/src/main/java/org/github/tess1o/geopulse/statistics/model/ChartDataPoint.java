package org.github.tess1o.geopulse.statistics.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for chart data point from SQL queries.
 * Represents a single data point with a label and distance value.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChartDataPoint {
    private String label;
    private Double distanceKm;
}
