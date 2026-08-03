package org.github.tess1o.geopulse.weather.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;
import org.locationtech.jts.geom.Point;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@ApplicationScoped
public class WeatherSamplingPolicy {

    private static final Duration LONG_ITEM_THRESHOLD = Duration.ofHours(2);
    private static final Duration DEFAULT_SPACING = Duration.ofHours(2);
    private static final int MAX_STAY_SAMPLES_PER_24H_SEGMENT = 4;
    private static final int MAX_TRIP_SAMPLES = 8;

    public List<WeatherSampleCandidate> forStay(TimelineStayEntity stay, WeatherTargetSource source, int priority) {
        if (stay == null || stay.getLocation() == null) {
            return List.of();
        }
        Instant start = truncateToHour(stay.getTimestamp());
        Instant end = stay.getTimestamp().plusSeconds(stay.getStayDuration());
        List<Instant> sampleTimes = sampleTimes(start, end, false);
        Point point = stay.getLocation();
        return sampleTimes.stream()
                .map(sampleTime -> new WeatherSampleCandidate(point.getY(), point.getX(), sampleTime, source, priority))
                .toList();
    }

    public List<Instant> sampleTimesForStay(Instant start, long durationSeconds, Instant rangeStart, Instant rangeEnd) {
        if (start == null || durationSeconds <= 0) {
            return List.of();
        }
        return sampleTimes(start, start.plusSeconds(durationSeconds), false, rangeStart, rangeEnd);
    }

    public List<Instant> sampleTimesForTrip(TimelineTripEntity trip) {
        if (trip == null) {
            return List.of();
        }
        Instant start = truncateToHour(trip.getTimestamp());
        Instant end = trip.getTimestamp().plusSeconds(trip.getTripDuration());
        return sampleTimes(start, end, true);
    }

    public List<Instant> sampleTimesForTrip(Instant start, long durationSeconds, Instant rangeStart, Instant rangeEnd) {
        if (start == null || durationSeconds <= 0) {
            return List.of();
        }
        return sampleTimes(start, start.plusSeconds(durationSeconds), true, rangeStart, rangeEnd);
    }

    public Instant ongoingSampleTime(Instant now, int intervalMinutes) {
        long intervalSeconds = Math.max(30, intervalMinutes) * 60L;
        long epochSecond = now.getEpochSecond();
        long bucketStart = epochSecond - (epochSecond % intervalSeconds);
        return Instant.ofEpochSecond(bucketStart).truncatedTo(ChronoUnit.HOURS);
    }

    private List<Instant> sampleTimes(Instant start, Instant end, boolean trip) {
        return sampleTimes(start, end, trip, null, null);
    }

    private List<Instant> sampleTimes(Instant start, Instant end, boolean trip, Instant rangeStart, Instant rangeEnd) {
        if (start == null || end == null || !end.isAfter(start)) {
            return List.of();
        }

        Duration duration = Duration.between(start, end);
        if (duration.compareTo(LONG_ITEM_THRESHOLD) < 0) {
            return inRange(List.of(truncateToHour(start.plus(duration.dividedBy(2)))), rangeStart, rangeEnd);
        }

        long durationHours = Math.max(1, duration.toHours());
        int desiredCount = Math.max(1, (int) Math.ceil((double) durationHours / DEFAULT_SPACING.toHours()));
        if (trip) {
            desiredCount = Math.min(MAX_TRIP_SAMPLES, desiredCount);
        } else {
            long segments = Math.max(1, (long) Math.ceil(duration.toHours() / 24.0));
            desiredCount = Math.min((int) (segments * MAX_STAY_SAMPLES_PER_24H_SEGMENT), desiredCount);
        }

        if (desiredCount <= 1) {
            return inRange(List.of(truncateToHour(start.plus(duration.dividedBy(2)))), rangeStart, rangeEnd);
        }

        double stepSeconds = duration.toSeconds() / (double) desiredCount;
        int firstIndex = candidateIndex(rangeStart, start, stepSeconds, desiredCount, false);
        int lastIndex = candidateIndex(rangeEnd, start, stepSeconds, desiredCount, true);
        List<Instant> result = new ArrayList<>(Math.max(0, lastIndex - firstIndex + 1));
        for (int i = firstIndex; i <= lastIndex; i++) {
            long offsetSeconds = Math.round((i + 0.5) * stepSeconds);
            Instant sampleAt = start.plusSeconds(Math.max(0, Math.min(duration.toSeconds(), offsetSeconds)));
            result.add(truncateToHour(sampleAt));
        }

        return inRange(result, rangeStart, rangeEnd);
    }

    private int candidateIndex(Instant boundary, Instant start, double stepSeconds, int desiredCount, boolean upper) {
        if (boundary == null) {
            return upper ? desiredCount - 1 : 0;
        }

        double offsetSeconds = Duration.between(start, boundary).toMillis() / 1000.0;
        if (upper) {
            offsetSeconds += ChronoUnit.HOURS.getDuration().toSeconds();
        }
        int index = (int) Math.floor((offsetSeconds / stepSeconds) - 0.5);
        index += upper ? 2 : -2;
        return Math.max(0, Math.min(desiredCount - 1, index));
    }

    private List<Instant> inRange(List<Instant> values, Instant rangeStart, Instant rangeEnd) {
        LinkedHashSet<Instant> result = new LinkedHashSet<>();
        for (Instant value : values) {
            if (rangeStart != null && value.isBefore(rangeStart)) {
                continue;
            }
            if (rangeEnd != null && value.isAfter(rangeEnd)) {
                continue;
            }
            result.add(value);
        }
        return List.copyOf(result);
    }

    public Instant truncateToHour(Instant value) {
        return value.truncatedTo(ChronoUnit.HOURS);
    }
}
