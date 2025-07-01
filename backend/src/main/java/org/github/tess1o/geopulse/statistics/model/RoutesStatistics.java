package org.github.tess1o.geopulse.statistics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RoutesStatistics {
    private int uniqueRoutesCount;
    private double longestTripDuration;
    private double avgTripDuration;
    private double longestTripDistance;
    private MostCommonRoute mostCommonRoute;
}
