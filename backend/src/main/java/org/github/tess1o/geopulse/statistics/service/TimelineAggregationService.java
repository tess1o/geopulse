package org.github.tess1o.geopulse.statistics.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service responsible for basic timeline data aggregations and mathematical calculations.
 * Handles distance totals, time calculations, averages, and speed computations.
 */
@ApplicationScoped
public class TimelineAggregationService {

    /**
     * Calculates total distance traveled from all trips.
     *
     * @param timeline the movement timeline data
     * @return total distance in kilometers
     */
    public double getTotalDistance(MovementTimelineDTO timeline) {
        return timeline.getTrips()
                .stream()
                .mapToDouble(TimelineTripDTO::getDistanceKm)
                .sum();
    }

    /**
     * Calculates total time spent moving from all trips.
     *
     * @param timeline the movement timeline data
     * @return total moving time in minutes
     */
    public long getTimeMoving(MovementTimelineDTO timeline) {
        return timeline.getTrips()
                .stream()
                .mapToLong(TimelineTripDTO::getTripDuration)
                .sum();
    }

    /**
     * Calculates average daily distance traveled.
     * Groups trips by date and calculates average distance per day.
     *
     * @param timeline the movement timeline data
     * @return average daily distance in kilometers
     */
    public double getDailyAverage(MovementTimelineDTO timeline) {
        Map<LocalDate, Double> collect = timeline.getTrips()
                .stream()
                .collect(Collectors.groupingBy(
                        trip -> trip.getTimestamp()
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate(),
                        Collectors.summingDouble(TimelineTripDTO::getDistanceKm)
                ));
        return collect.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    /**
     * Calculates average speed from total distance and time moving.
     * Handles division by zero gracefully.
     *
     * @param totalDistanceKm total distance in kilometers
     * @param timeMovingMinutes total moving time in minutes
     * @return average speed in km/h
     */
    public double getAverageSpeed(double totalDistanceKm, long timeMovingMinutes) {
        if (timeMovingMinutes <= 0) {
            return 0.0;
        }
        return totalDistanceKm / (timeMovingMinutes / 60.0);
    }
}