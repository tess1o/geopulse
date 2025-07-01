package org.github.tess1o.geopulse.statistics.service;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.github.tess1o.geopulse.statistics.model.TopPlace;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineStayLocationDTO;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service responsible for analyzing places and location statistics from timeline data.
 * Handles top places calculation, unique location counting, and location-based aggregations.
 */
@ApplicationScoped
public class PlacesAnalysisService {

    /**
     * Calculates the top 5 places by visit count and duration.
     *
     * @param timeline the movement timeline data
     * @return list of top places sorted by visits and duration
     */
    public List<TopPlace> getPlacesStatistics(MovementTimelineDTO timeline) {
        Map<String, StayStats> statsByLocation = timeline.getStays().stream()
                .collect(Collectors.groupingBy(
                        TimelineStayLocationDTO::getLocationName,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> new StayStats(list.size(), list.stream().mapToLong(TimelineStayLocationDTO::getStayDuration).sum())
                        )
                ));

        // Sort by count (or totalDuration), then get top 5
        List<Map.Entry<String, StayStats>> top5 = statsByLocation.entrySet()
                .stream()
                .sorted(topPlacesComparator())
                .limit(5)
                .toList();

        return top5.stream()
                .map(e -> TopPlace.builder()
                        .name(e.getKey())
                        .visits(e.getValue().getCount())
                        .duration(e.getValue().totalDuration)
                        .coordinates(timeline.getStays().stream()
                                .filter(s -> s.getLocationName().equals(e.getKey()))
                                .findFirst()
                                .map(this::coordinates)
                                .orElseGet(() -> new double[0])
                        )
                        .build())
                .toList();
    }

    /**
     * Counts the number of unique locations visited.
     *
     * @param timeline the movement timeline data
     * @return count of unique locations
     */
    public long getUniqueLocationsCount(MovementTimelineDTO timeline) {
        return timeline.getStays().stream()
                .map(TimelineStayLocationDTO::getLocationName)
                .distinct()
                .count();
    }

    /**
     * Extracts coordinates from a stay location.
     */
    private double[] coordinates(TimelineStayLocationDTO stay) {
        return new double[]{stay.getLatitude(), stay.getLongitude()};
    }

    /**
     * Comparator for sorting top places by visit count and duration.
     */
    private static Comparator<Map.Entry<String, StayStats>> topPlacesComparator() {
        return Comparator
                .comparingLong((Map.Entry<String, StayStats> e) -> e.getValue().count)
                .reversed()
                .thenComparing(
                        Comparator.comparingLong((Map.Entry<String, StayStats> e) -> e.getValue().totalDuration).reversed()
                );
    }

    /**
     * Internal class to hold stay statistics for a location.
     */
    @Getter
    @AllArgsConstructor
    private static class StayStats {
        long count;
        long totalDuration;
    }
}