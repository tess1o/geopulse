package org.github.tess1o.geopulse.digest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DigestMetrics {
    private double totalDistance; // in meters
    private int activeDays;
    private int citiesVisited;
    private int tripCount;
    private int stayCount;

    // Distance by trip type (in meters)
    private double carDistance;
    private double walkDistance;
    private double bicycleDistance;
    private double trainDistance;
    private double flightDistance;
    private double unknownDistance;

    // Other enhanced metrics
    private long timeMoving; // in seconds
    private double dailyAverageDistance; // in meters
}
