package org.github.tess1o.geopulse.statistics.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.statistics.model.BarChartData;
import org.github.tess1o.geopulse.statistics.model.ChartGroupMode;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Service responsible for generating chart data from timeline information.
 * Handles both daily and weekly grouping modes.
 */
@ApplicationScoped
public class ChartDataService {

    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM/dd");

    /**
     * Generates chart data based on the specified grouping mode.
     *
     * @param timeline       the movement timeline data
     * @param car
     * @param chartGroupMode the grouping mode (DAYS or WEEKS)
     * @return chart data with labels and distance values
     */
    public BarChartData getDistanceChartData(MovementTimelineDTO timeline, TripType tripType, ChartGroupMode chartGroupMode) {
        if (chartGroupMode == ChartGroupMode.WEEKS) {
            return getBarChartDataByWeeks(timeline, tripType);
        }
        return getBarChartDataByDays(timeline, tripType);
    }

    /**
     * Generates weekly chart data grouped by start of week (Monday).
     */
    private BarChartData getBarChartDataByWeeks(MovementTimelineDTO timeline, TripType tripType) {
        ZoneId zone = ZoneId.of("UTC");

        // Group by start-of-week (Monday)
        Map<LocalDate, Double> distanceByWeek = timeline.getTrips().stream()
                .filter(matchesTripType(tripType))
                .collect(Collectors.groupingBy(
                        trip -> getStartOfWeek(trip.getTimestamp().atZone(zone).toLocalDate()),
                        TreeMap::new, // ensures sorted by week
                        Collectors.summingDouble(TimelineTripDTO::getDistanceKm)
                ));

        // Build labels and distances
        String[] labels = distanceByWeek.keySet().stream()
                .map(date -> date.format(DATE_TIME_FORMATTER))
                .toArray(String[]::new);

        double[] distances = distanceByWeek.values().stream()
                .mapToDouble(Double::doubleValue)
                .toArray();

        return new BarChartData(labels, distances);
    }

    /**
     * Generates daily chart data grouped by day of week.
     */
    private BarChartData getBarChartDataByDays(MovementTimelineDTO timeline, TripType tripType) {
        ZoneId zone = ZoneId.of("UTC");
        // Step 1: Group by date, summing distances
        Map<LocalDate, Double> distanceByDate = timeline.getTrips().stream()
                .filter(matchesTripType(tripType))
                .collect(Collectors.groupingBy(
                        trip -> trip.getTimestamp().atZone(zone).toLocalDate(),
                        Collectors.summingDouble(TimelineTripDTO::getDistanceKm)
                ));

        // Step 2: Sort by LocalDate ascending
        List<LocalDate> sortedDates = distanceByDate.keySet().stream()
                .sorted()
                .toList();

        // Step 3: Build final arrays
        String[] labels = sortedDates.stream()
                .map(date -> date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase())
                .toArray(String[]::new);

        double[] distances = sortedDates.stream()
                .mapToDouble(date -> distanceByDate.getOrDefault(date, 0.0))
                .toArray();

        return new BarChartData(labels, distances);
    }

    private static Predicate<TimelineTripDTO> matchesTripType(TripType tripType) {
        return t -> t.getMovementType() == null || t.getMovementType().isBlank() || t.getMovementType().equals(tripType.name());
    }

    /**
     * Gets the start of week (Monday) for a given date.
     */
    private static LocalDate getStartOfWeek(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }
}