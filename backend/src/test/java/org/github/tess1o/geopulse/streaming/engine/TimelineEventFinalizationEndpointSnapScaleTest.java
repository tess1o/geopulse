package org.github.tess1o.geopulse.streaming.engine;

import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionWaypointDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Snapping runs over the whole regenerated range, which on a multi-year account holds tens of thousands of trips and
 * stays. It must stay linear in the timeline size and decide exactly what the original pairwise scan decided.
 */
@Tag("unit")
class TimelineEventFinalizationEndpointSnapScaleTest {

    private static final Instant START = Instant.parse("2020-01-01T00:00:00Z");

    private final TimelineEventFinalizationService finalization = new TimelineEventFinalizationService();

    @Test
    void snapsAMultiYearTimelineQuickly() {
        List<TimelineEvent> events = commuteYears(20_000, new Random(7));
        assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> finalization.snapTripEndpointsToAdjacentFavoriteStays(events));
    }

    @Test
    void decidesExactlyWhatThePairwiseScanDecided() {
        for (long seed = 1; seed <= 300; seed++) {
            Random random = new Random(seed);
            List<TimelineEvent> expected = randomTimeline(random);
            List<TimelineEvent> actual = copyAll(expected);

            PairwiseOracle.snap(expected);
            finalization.snapTripEndpointsToAdjacentFavoriteStays(actual);

            assertThat(endpoints(actual)).as("seed %d", seed).isEqualTo(endpoints(expected));
        }
    }

    /** Stays and trips that overlap, touch, leave gaps and come in any order, with and without favorites. */
    private static List<TimelineEvent> randomTimeline(Random random) {
        List<TimelineEvent> events = new ArrayList<>();
        int count = 2 + random.nextInt(25);
        for (int i = 0; i < count; i++) {
            // Sub-second offsets exercise the whole-second gap comparison and its ties.
            Instant start = START.plusSeconds(random.nextInt(4 * 3600)).plusMillis(random.nextInt(3) * 400L);
            long duration = random.nextInt(4) == 0 ? 0 : 1 + random.nextInt(3600);
            if (random.nextBoolean()) {
                events.add(stay(start, duration, random.nextInt(3) == 0 ? null : (long) random.nextInt(4),
                        random.nextDouble(), random.nextDouble()));
            } else {
                Trip trip = trip(start, duration, random.nextDouble(), random.nextDouble(),
                        random.nextDouble(), random.nextDouble());
                if (random.nextInt(8) == 0) {
                    trip.setManualRoute(List.of(new TripReconstructionWaypointDto(), new TripReconstructionWaypointDto()));
                }
                events.add(trip);
            }
        }
        return events;
    }

    private static List<TimelineEvent> commuteYears(int days, Random random) {
        List<TimelineEvent> events = new ArrayList<>();
        Instant cursor = START;
        for (int day = 0; day < days; day++) {
            long stay = 3600 + random.nextInt(8 * 3600);
            events.add(stay(cursor, stay, random.nextBoolean() ? 1L : null, 49.84, 24.03));
            cursor = cursor.plusSeconds(stay);
            long drive = 600 + random.nextInt(3600);
            events.add(trip(cursor, drive, 49.84, 24.03, 49.83, 24.07));
            cursor = cursor.plusSeconds(drive);
        }
        return events;
    }

    private static List<String> endpoints(List<TimelineEvent> events) {
        return events.stream().filter(Trip.class::isInstance).map(Trip.class::cast)
                .map(trip -> trip.getStartPoint().getLatitude() + "," + trip.getStartPoint().getLongitude()
                        + " -> " + trip.getEndPoint().getLatitude() + "," + trip.getEndPoint().getLongitude())
                .toList();
    }

    private static List<TimelineEvent> copyAll(List<TimelineEvent> events) {
        List<TimelineEvent> copies = new ArrayList<>();
        for (TimelineEvent event : events) {
            if (event instanceof Stay stay) {
                copies.add(Stay.builder().startTime(stay.getStartTime()).duration(stay.getDuration())
                        .favoriteId(stay.getFavoriteId()).latitude(stay.getLatitude())
                        .longitude(stay.getLongitude()).build());
            } else if (event instanceof Trip trip) {
                copies.add(Trip.builder().startTime(trip.getStartTime()).duration(trip.getDuration())
                        .manualRoute(trip.getManualRoute()).startPoint(trip.getStartPoint())
                        .endPoint(trip.getEndPoint()).build());
            }
        }
        return copies;
    }

    private static Stay stay(Instant start, long seconds, Long favoriteId, double latitude, double longitude) {
        return Stay.builder().startTime(start).duration(Duration.ofSeconds(seconds)).favoriteId(favoriteId)
                .latitude(latitude).longitude(longitude).build();
    }

    private static Trip trip(Instant start, long seconds, double latitude, double longitude,
                             double endLatitude, double endLongitude) {
        return Trip.builder().startTime(start).duration(Duration.ofSeconds(seconds))
                .startPoint(GPSPoint.builder().timestamp(start).latitude(latitude).longitude(longitude).build())
                .endPoint(GPSPoint.builder().timestamp(start.plusSeconds(seconds))
                        .latitude(endLatitude).longitude(endLongitude).build())
                .build();
    }

    /** The pairwise scan this replaces, kept as the reference for what snapping decides. */
    private static final class PairwiseOracle {
        static void snap(List<TimelineEvent> events) {
            List<Stay> stays = events.stream().filter(Stay.class::isInstance).map(Stay.class::cast).toList();
            List<Trip> trips = events.stream().filter(Trip.class::isInstance).map(Trip.class::cast).toList();
            if (stays.isEmpty() || trips.isEmpty()) return;
            for (Trip trip : trips) {
                if (trip.getManualRoute() != null) continue;
                Instant tripStart = trip.getStartTime();
                Instant tripEnd = trip.getEndTime();
                Stay origin = closestStay(stays, tripStart, true);
                if (origin != null && origin.getFavoriteId() != null
                        && !hasAnotherTripBetween(origin.getEndTime(), tripStart, trips, trip)) {
                    trip.setStartPoint(at(trip.getStartPoint(), origin.getLatitude(), origin.getLongitude()));
                }
                Stay destination = closestStay(stays, tripEnd, false);
                if (destination != null && destination.getFavoriteId() != null
                        && !hasAnotherTripBetween(tripEnd, destination.getStartTime(), trips, trip)) {
                    trip.setEndPoint(at(trip.getEndPoint(), destination.getLatitude(), destination.getLongitude()));
                }
            }
        }

        private static Stay closestStay(List<Stay> stays, Instant reference, boolean before) {
            Stay closest = null;
            long minGapSeconds = Long.MAX_VALUE;
            for (Stay stay : stays) {
                if (stay.getStartTime() == null) continue;
                Instant edge = before ? stay.getEndTime() : stay.getStartTime();
                if (before ? edge.isAfter(reference) : edge.isBefore(reference)) continue;
                long gap = Math.abs(Duration.between(edge, reference).toSeconds());
                if (gap < minGapSeconds) {
                    minGapSeconds = gap;
                    closest = stay;
                }
            }
            return closest;
        }

        private static boolean hasAnotherTripBetween(Instant from, Instant to, List<Trip> trips, Trip current) {
            if (from == null || to == null || !to.isAfter(from)) return false;
            for (Trip other : trips) {
                if (other == current || other.getStartTime() == null) continue;
                if (other.getStartTime().isBefore(to) && other.getEndTime().isAfter(from)) return true;
            }
            return false;
        }

        private static GPSPoint at(GPSPoint original, double latitude, double longitude) {
            return GPSPoint.builder().timestamp(original.getTimestamp()).latitude(latitude).longitude(longitude)
                    .build();
        }
    }
}
