package org.github.tess1o.geopulse.statistics.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for route frequency results from SQL queries.
 * Represents a route between two locations with its frequency count.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteFrequencyResult {
    private String fromLocation;
    private String toLocation;
    private Long frequency;
}
