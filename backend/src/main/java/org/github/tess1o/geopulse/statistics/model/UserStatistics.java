package org.github.tess1o.geopulse.statistics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class UserStatistics {
    private double totalDistance;
    private long timeMoving;
    private double dailyAverage; //in km;
    private long uniqueLocationsCount;
    private double averageSpeed;
    private MostActiveDayDto mostActiveDay;
    private List<TopPlace> places;
    private RoutesStatistics routes;
    private BarChartData distanceCarChart;
    private BarChartData distanceWalkChart;
}
