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
    private double longestTripDurationSeconds;
    private double avgTripDurationSeconds;
    private double longestTripDistanceMeters;
    private MostCommonRoute mostCommonRoute;
}
