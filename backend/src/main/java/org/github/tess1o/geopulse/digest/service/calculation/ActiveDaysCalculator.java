package org.github.tess1o.geopulse.digest.service.calculation;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

/**
 * Calculates active days from timeline data.
 * Eliminates duplication - this logic was repeated 3 times in DigestServiceImpl.
 */
@ApplicationScoped
public class ActiveDaysCalculator {

    /**
     * Calculates the set of unique active days from timeline data within the specified period.
     *
     * @param timeline Timeline containing stays and trips
     * @param start    Period start instant
     * @param end      Period end instant
     * @param zoneId   User's timezone
     * @return Set of unique LocalDate values representing active days
     */
    public Set<LocalDate> calculateActiveDays(MovementTimelineDTO timeline, Instant start, Instant end, ZoneId zoneId) {
        LocalDate periodStart = LocalDate.ofInstant(start, zoneId);
        LocalDate periodEnd = LocalDate.ofInstant(end, zoneId);
        Set<LocalDate> activeDays = new HashSet<>();

        // Add days from stays
        if (timeline.getStays() != null) {
            timeline.getStays().forEach(stay -> {
                LocalDate stayDate = LocalDate.ofInstant(stay.getTimestamp(), zoneId);
                if (!stayDate.isBefore(periodStart) && !stayDate.isAfter(periodEnd)) {
                    activeDays.add(stayDate);
                }
            });
        }

        // Add days from trips
        if (timeline.getTrips() != null) {
            timeline.getTrips().forEach(trip -> {
                LocalDate tripDate = LocalDate.ofInstant(trip.getTimestamp(), zoneId);
                if (!tripDate.isBefore(periodStart) && !tripDate.isAfter(periodEnd)) {
                    activeDays.add(tripDate);
                }
            });
        }

        return activeDays;
    }

    /**
     * Convenience method that returns the count of active days.
     *
     * @param timeline Timeline containing stays and trips
     * @param start    Period start instant
     * @param end      Period end instant
     * @param zoneId   User's timezone
     * @return Count of unique active days
     */
    public int calculateActiveDaysCount(MovementTimelineDTO timeline, Instant start, Instant end, ZoneId zoneId) {
        return calculateActiveDays(timeline, start, end, zoneId).size();
    }
}
