package org.github.tess1o.geopulse.statistics.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.statistics.model.MostCommonRoute;
import org.github.tess1o.geopulse.statistics.model.RoutesStatistics;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;

import java.util.HashMap;
import java.util.Map;

/**
 * Service responsible for analyzing route statistics from timeline data.
 * Handles trip duration analysis, route identification, and travel pattern statistics.
 */
@ApplicationScoped
public class RoutesAnalysisService {

    /**
     * Calculates comprehensive route statistics including trip durations,
     * route frequency, and travel patterns.
     *
     * @param timeline the movement timeline data
     * @return routes statistics with trip and route information
     */
    public RoutesStatistics getRoutesStatistics(MovementTimelineDTO timeline) {
        Map<String, Integer> routes = new HashMap<>();
        for (int i = 0; i < timeline.getStays().size() - 1; i++) {
            TimelineStayLocationDTO current = timeline.getStays().get(i);
            TimelineStayLocationDTO next = timeline.getStays().get(i + 1);
            String key = current.getLocationName() + " -> " + next.getLocationName();
            routes.merge(key, 1, Integer::sum);
        }

        double averageTripDuration = timeline.getTrips()
                .stream()
                .mapToDouble(TimelineTripDTO::getTripDuration)
                .average()
                .orElse(0.0);

        double longestTripDuration = timeline.getTrips()
                .stream()
                .mapToDouble(TimelineTripDTO::getTripDuration)
                .max()
                .orElse(0.0);

        double longestTripDistance = timeline.getTrips()
                .stream()
                .mapToDouble(TimelineTripDTO::getDistanceKm)
                .max()
                .orElse(0.0);

        Map.Entry<String, Integer> mostCommonRoute = routes.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        MostCommonRoute mostCommonRouteDto = mostCommonRoute != null
                ? new MostCommonRoute(mostCommonRoute.getKey(), mostCommonRoute.getValue())
                : new MostCommonRoute("", 0);

        return RoutesStatistics.builder()
                .avgTripDuration(averageTripDuration)
                .uniqueRoutesCount(routes.size())
                .mostCommonRoute(mostCommonRouteDto)
                .longestTripDuration(longestTripDuration)
                .longestTripDistance(longestTripDistance)
                .build();
    }
}