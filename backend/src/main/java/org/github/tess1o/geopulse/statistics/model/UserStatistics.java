package org.github.tess1o.geopulse.statistics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class UserStatistics {
    private double totalDistanceMeters;
    private long timeMoving; //seconds
    private double dailyAverageDistanceMeters; //in m;
    private long uniqueLocationsCount;
    private double averageSpeed; //km / h
    private MostActiveDayDto mostActiveDay;
    private List<TopPlace> places;
    private RoutesStatistics routes;
    // Map of trip type to chart data for all trip types
    private Map<String, BarChartData> distanceChartsByTripType;
}
