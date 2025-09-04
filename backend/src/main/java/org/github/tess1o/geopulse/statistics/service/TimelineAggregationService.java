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
     * @return total distance in meters
     */
    public double getTotalDistanceMeters(MovementTimelineDTO timeline) {
        return timeline.getTrips()
                .stream()
                .mapToDouble(trip -> trip.getDistanceMeters()) // Convert meters to m
                .sum();
    }

    /**
     * Calculates total time spent moving from all trips.
     *
     * @param timeline the movement timeline data
     * @return total moving time in seconds
     */
    public long getTimeMovingSeconds(MovementTimelineDTO timeline) {
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
     * @return average daily distance in meters
     */
    public double getDailyDistanceAverageMeters(MovementTimelineDTO timeline) {
        Map<LocalDate, Double> collect = timeline.getTrips()
                .stream()
                .collect(Collectors.groupingBy(
                        trip -> trip.getTimestamp()
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate(),
                        Collectors.summingDouble(trip -> trip.getDistanceMeters()) // Convert meters to km
                ));
        return collect.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    /**
     * Calculates average speed from total distance and time moving.
     * Handles division by zero gracefully.
     *
     * @param totalDistanceMeters total distance in meters
     * @param timeMovingSeconds total moving time in seconds
     * @return average speed in km/h
     */
    public double getAverageSpeed(double totalDistanceMeters, long timeMovingSeconds) {
        if (timeMovingSeconds <= 0) {
            return 0.0;
        }
        double metersPerSecond = totalDistanceMeters / timeMovingSeconds;
        return metersPerSecond * 3.6; // convert m/s to km/h
    }
}