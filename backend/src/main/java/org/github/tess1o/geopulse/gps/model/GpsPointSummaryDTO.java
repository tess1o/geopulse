package org.github.tess1o.geopulse.gps.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for GPS point summary statistics.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GpsPointSummaryDTO {
    private long totalPoints;
    private long pointsToday;
    private Instant firstPointDate;
    private Instant lastPointDate;
}