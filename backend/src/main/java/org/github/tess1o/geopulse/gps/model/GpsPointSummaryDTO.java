package org.github.tess1o.geopulse.gps.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for GPS point summary statistics.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GpsPointSummaryDTO {
    private long totalPoints;
    private long pointsToday;
    private String firstPointDate;
    private String lastPointDate;
}