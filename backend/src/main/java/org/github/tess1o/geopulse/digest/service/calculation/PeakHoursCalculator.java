package org.github.tess1o.geopulse.digest.service.calculation;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Calculates peak hours (most active time periods) from timeline data.
 */
@ApplicationScoped
public class PeakHoursCalculator {

    /**
     * Calculate peak hours based on trip activity.
     * Returns top 2 time ranges when user is most active.
     *
     * @param timeline Timeline containing trip data
     * @param zoneId   User's timezone
     * @return Array of formatted hour ranges (e.g., ["8-10 AM", "5-7 PM"])
     */
    public String[] calculatePeakHours(MovementTimelineDTO timeline, ZoneId zoneId) {
        if (timeline.getTrips() == null || timeline.getTrips().isEmpty()) {
            return new String[0];
        }

        // Count trips by hour of day
        Map<Integer, Long> tripsByHour = timeline.getTrips().stream()
                .collect(Collectors.groupingBy(
                        trip -> LocalDateTime.ofInstant(trip.getTimestamp(), zoneId).getHour(),
                        Collectors.counting()
                ));

        if (tripsByHour.isEmpty()) {
            return new String[0];
        }

        // Get top 4-5 most active hours (to have enough to group)
        List<Integer> topHours = tripsByHour.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .sorted() // Sort by hour (not trip count) to find consecutive hours
                .toList();

        // Group consecutive hours into ranges
        List<HourRange> ranges = new ArrayList<>();
        int rangeStart = topHours.get(0);
        int rangeEnd = topHours.get(0);
        long rangeTripCount = tripsByHour.get(topHours.get(0));

        for (int i = 1; i < topHours.size(); i++) {
            int currentHour = topHours.get(i);
            int prevHour = topHours.get(i - 1);

            // Check if consecutive (handles 23->0 wrap)
            boolean isConsecutive = (currentHour == prevHour + 1) ||
                                   (prevHour == 23 && currentHour == 0);

            if (isConsecutive) {
                // Extend current range
                rangeEnd = currentHour;
                rangeTripCount += tripsByHour.get(currentHour);
            } else {
                // Save current range and start new one
                ranges.add(new HourRange(rangeStart, rangeEnd + 1, rangeTripCount));
                rangeStart = currentHour;
                rangeEnd = currentHour;
                rangeTripCount = tripsByHour.get(currentHour);
            }
        }
        // Don't forget the last range
        ranges.add(new HourRange(rangeStart, rangeEnd + 1, rangeTripCount));

        // Return top 2 ranges by trip count
        return ranges.stream()
                .sorted(Comparator.comparingLong(HourRange::totalTrips).reversed())
                .limit(2)
                .map(range -> formatHourRange(range.start, range.end))
                .toArray(String[]::new);
    }

    /**
     * Format hour range for display (e.g., "8-10 AM", "11 AM-1 PM")
     */
    private String formatHourRange(int startHour, int endHour) {
        String startPeriod = startHour < 12 ? "AM" : "PM";
        String endPeriod = endHour < 12 ? "AM" : "PM";

        int displayStartHour = startHour == 0 ? 12 : (startHour > 12 ? startHour - 12 : startHour);
        int displayEndHour = endHour == 0 ? 12 : (endHour > 12 ? endHour - 12 : endHour);

        if (startPeriod.equals(endPeriod)) {
            return displayStartHour + "-" + displayEndHour + " " + endPeriod;
        } else {
            return displayStartHour + " " + startPeriod + "-" + displayEndHour + " " + endPeriod;
        }
    }

    /**
     * Helper record to store hour ranges with their trip counts
     */
    private record HourRange(int start, int end, long totalTrips) {
    }
}
