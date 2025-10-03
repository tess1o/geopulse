package org.github.tess1o.geopulse.digest.service.calculation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.digest.model.DigestHighlight;
import org.github.tess1o.geopulse.statistics.model.TopPlace;
import org.github.tess1o.geopulse.statistics.model.UserStatistics;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Analyzes timeline data to detect interesting highlights and patterns.
 */
@ApplicationScoped
public class DigestHighlightsAnalyzer {

    @Inject
    PeakHoursCalculator peakHoursCalculator;

    /**
     * Build highlights from statistics and timeline data.
     *
     * @param stats    User statistics
     * @param timeline Timeline data
     * @param start    Period start time
     * @param end      Period end time
     * @param zoneId   User's timezone
     * @return DigestHighlight with all detected highlights
     */
    public DigestHighlight buildHighlights(UserStatistics stats, MovementTimelineDTO timeline,
                                          Instant start, Instant end, ZoneId zoneId) {
        DigestHighlight.TripHighlight longestTrip = null;
        DigestHighlight.BusiestDay busiestDay = null;

        // Define period boundaries for filtering
        LocalDate periodStart = LocalDate.ofInstant(start, zoneId);
        LocalDate periodEnd = LocalDate.ofInstant(end, zoneId);

        // Find longest trip (within period)
        if (timeline.getTrips() != null && !timeline.getTrips().isEmpty()) {
            var maxTrip = timeline.getTrips().stream()
                    .filter(trip -> {
                        LocalDate tripDate = LocalDate.ofInstant(trip.getTimestamp(), zoneId);
                        return !tripDate.isBefore(periodStart) && !tripDate.isAfter(periodEnd);
                    })
                    .max(Comparator.comparingDouble(TimelineTripDTO::getDistanceMeters))
                    .orElse(null);

            if (maxTrip != null) {
                String destination = "Trip"; // Simplified for now as we don't have destination name

                longestTrip = DigestHighlight.TripHighlight.builder()
                        .distance(maxTrip.getDistanceMeters())
                        .destination(destination)
                        .date(maxTrip.getTimestamp())
                        .build();
            }
        }

        // Find most visited place
        DigestHighlight.PlaceHighlight mostVisited = null;
        if (stats.getPlaces() != null && !stats.getPlaces().isEmpty()) {
            TopPlace topPlace = stats.getPlaces().get(0);
            mostVisited = DigestHighlight.PlaceHighlight.builder()
                    .name(topPlace.getName())
                    .visits((int) topPlace.getVisits())
                    .build();
        }

        // Find busiest day (day with most trips) - only within period
        if (timeline.getTrips() != null && !timeline.getTrips().isEmpty()) {
            Map<LocalDate, Long> tripsByDay = timeline.getTrips().stream()
                    .map(trip -> LocalDate.ofInstant(trip.getTimestamp(), zoneId))
                    .filter(tripDate -> !tripDate.isBefore(periodStart) && !tripDate.isAfter(periodEnd))
                    .collect(Collectors.groupingBy(
                            tripDate -> tripDate,
                            Collectors.counting()
                    ));

            var busiestEntry = tripsByDay.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);

            if (busiestEntry != null) {
                LocalDate busiestDate = busiestEntry.getKey();
                double dayDistance = timeline.getTrips().stream()
                        .filter(trip -> LocalDate.ofInstant(trip.getTimestamp(), zoneId).equals(busiestDate))
                        .mapToDouble(TimelineTripDTO::getDistanceMeters)
                        .sum();

                busiestDay = DigestHighlight.BusiestDay.builder()
                        .date(busiestDate.atStartOfDay(zoneId).toInstant())
                        .trips(busiestEntry.getValue().intValue())
                        .distance(dayDistance)
                        .build();
            }
        }

        // Calculate peak hours from trips
        String[] peakHours = peakHoursCalculator.calculatePeakHours(timeline, zoneId);

        return DigestHighlight.builder()
                .longestTrip(longestTrip)
                .mostVisited(mostVisited)
                .busiestDay(busiestDay)
                .peakHours(peakHours)
                .build();
    }
}
