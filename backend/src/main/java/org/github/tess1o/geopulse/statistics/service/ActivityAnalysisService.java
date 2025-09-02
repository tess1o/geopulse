package org.github.tess1o.geopulse.statistics.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.statistics.model.MostActiveDayDto;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service responsible for analyzing activity patterns from timeline data.
 * Handles most active day detection and activity-based statistics.
 */
@ApplicationScoped
public class ActivityAnalysisService {

    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM/dd");

    /**
     * Identifies and analyzes the most active day from timeline data.
     * Returns detailed statistics for the day with highest travel distance.
     *
     * @param timeline the movement timeline data
     * @return most active day statistics or null if no active days found
     */
    public MostActiveDayDto getMostActiveDay(MovementTimelineDTO timeline) {
        LocalDate mostActiveDay = mostActiveDay(timeline);

        if (mostActiveDay == null) {
            return null;
        }

        List<TimelineTripDTO> trips = timeline.getTrips().stream()
                .filter(trip -> trip.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate().equals(mostActiveDay))
                .toList();
        List<TimelineStayLocationDTO> stays = timeline.getStays().stream()
                .filter(stay -> stay.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate().equals(mostActiveDay))
                .toList();

        double distanceTraveled = trips.stream()
                .mapToDouble(TimelineTripDTO::getDistanceKm)
                .sum();

        double tripDuration = trips.stream()
                .mapToDouble(TimelineTripDTO::getTripDuration)
                .sum();

        long locationsVisited = stays.stream()
                .map(TimelineStayLocationDTO::getLocationName)
                .distinct()
                .count();

        return MostActiveDayDto.builder()
                .date(DATE_TIME_FORMATTER.format(mostActiveDay))
                .day(mostActiveDay.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .travelTime(tripDuration)
                .locationsVisited(locationsVisited)
                .distanceTraveled(distanceTraveled)
                .build();
    }

    /**
     * Finds the date with the highest total travel distance.
     *
     * @param timeline the movement timeline data
     * @return the most active date or null if no trips found
     */
    private static LocalDate mostActiveDay(MovementTimelineDTO timeline) {
        Map<LocalDate, Double> distanceByDay = timeline.getTrips().stream()
                .collect(Collectors.groupingBy(
                        trip -> trip.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate(),
                        Collectors.summingDouble(TimelineTripDTO::getDistanceKm)
                ));

        return distanceByDay.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}