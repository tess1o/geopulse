package org.github.tess1o.geopulse.ai.model;

import org.github.tess1o.geopulse.statistics.model.MostActiveDayDto;
import org.github.tess1o.geopulse.statistics.model.RoutesStatistics;
import org.github.tess1o.geopulse.statistics.model.TopPlace;
import org.github.tess1o.geopulse.statistics.model.UserStatistics;

import java.util.List;

/** Movement statistics for a range without the distance charts. */
public record AIStatisticsDTO(
        double totalDistanceMeters,
        long timeMovingSeconds,
        double dailyAverageDistanceMeters,
        long uniqueLocationsCount,
        double averageSpeedKmh,
        MostActiveDayDto mostActiveDay,
        List<TopPlace> topPlaces,
        RoutesStatistics routes
) {

    public static AIStatisticsDTO from(UserStatistics statistics) {
        return new AIStatisticsDTO(statistics.getTotalDistanceMeters(), statistics.getTimeMoving(),
                statistics.getDailyAverageDistanceMeters(), statistics.getUniqueLocationsCount(),
                statistics.getAverageSpeed(), statistics.getMostActiveDay(), statistics.getPlaces(),
                statistics.getRoutes());
    }
}
