package org.github.tess1o.geopulse.weather.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.prometheus.GeoPulseWorkloadMetrics;
import org.github.tess1o.geopulse.shared.service.TimestampUtils;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.weather.dto.WeatherTargetQueueResponse;
import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;
import org.github.tess1o.geopulse.weather.repository.WeatherBackfillReconciliationRepository;
import org.github.tess1o.geopulse.weather.repository.WeatherSampleRepository;
import org.github.tess1o.geopulse.weather.repository.WeatherSampleTargetRepository;
import org.github.tess1o.geopulse.weather.repository.WeatherTargetBatchRow;
import org.locationtech.jts.geom.Point;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns timeline stays and trips into weather sample targets: every sample time of a range for historical
 * reconciliation, and the user's current position for ongoing sampling.
 */
@ApplicationScoped
public class WeatherTargetPlanner {

    private static final int ONGOING_PRIORITY = 100;

    @Inject
    WeatherConfigurationService configurationService;

    @Inject
    WeatherSamplingPolicy samplingPolicy;

    @Inject
    WeatherSampleRepository sampleRepository;

    @Inject
    WeatherSampleTargetRepository targetRepository;

    @Inject
    WeatherBackfillReconciliationRepository backfillReconciliationRepository;

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    EntityManager entityManager;

    @Inject
    GeoPulseWorkloadMetrics workloadMetrics;

    private WeatherWorkloadMetrics metrics() {
        return new WeatherWorkloadMetrics(workloadMetrics);
    }

    @SuppressWarnings("unchecked")
    WeatherTargetQueueResponse enqueueForChunk(UUID userId, Instant startTime, Instant endTime,
                                                       WeatherTargetSource source, int priority) {
        List<WeatherSampleCandidate> candidates = new ArrayList<>();

        long stageStart = metrics().start();
        List<Object[]> stayRows = entityManager.createNativeQuery("""
                SELECT timestamp, stay_duration, ST_Y(location), ST_X(location)
                FROM timeline_stays
                WHERE user_id = ?1
                  AND timestamp <= ?3
                  AND timestamp + (stay_duration * INTERVAL '1 second') >= ?2
                ORDER BY timestamp
                """)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .getResultList();
        metrics().recordStage(stageStart, "load_stays", source.name(), "success");
        for (Object[] row : stayRows) {
            Instant stayStart = TimestampUtils.getInstantSafe(row[0]);
            long durationSeconds = ((Number) row[1]).longValue();
            double latitude = ((Number) row[2]).doubleValue();
            double longitude = ((Number) row[3]).doubleValue();
            for (Instant targetAt : samplingPolicy.sampleTimesForStay(stayStart, durationSeconds, startTime, endTime)) {
                candidates.add(new WeatherSampleCandidate(latitude, longitude, targetAt, source, priority));
            }
        }

        stageStart = metrics().start();
        List<Object[]> tripRows = entityManager.createNativeQuery("""
                SELECT timestamp,
                       trip_duration,
                       ST_Y(start_point),
                       ST_X(start_point),
                       ST_Y(end_point),
                       ST_X(end_point)
                FROM timeline_trips
                WHERE user_id = ?1
                  AND timestamp <= ?3
                  AND timestamp + (trip_duration * INTERVAL '1 second') >= ?2
                ORDER BY timestamp
                """)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .getResultList();
        metrics().recordStage(stageStart, "load_trips", source.name(), "success");
        for (Object[] row : tripRows) {
            TimelineTripSlice trip = new TimelineTripSlice(
                    TimestampUtils.getInstantSafe(row[0]),
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).doubleValue(),
                    ((Number) row[3]).doubleValue(),
                    ((Number) row[4]).doubleValue(),
                    ((Number) row[5]).doubleValue()
            );
            candidates.addAll(tripCandidates(userId, trip, startTime, endTime, source, priority));
        }

        int skipped = 0;
        int validCandidates = 0;
        Map<WeatherTargetKey, WeatherTargetBatchRow> batchRows = new LinkedHashMap<>();
        for (WeatherSampleCandidate candidate : candidates) {
            if (!isValidCoordinate(candidate.latitude(), candidate.longitude())) {
                skipped++;
                continue;
            }

            validCandidates++;
            double latitudeBucket = configurationService.bucketCoordinate(candidate.latitude());
            double longitudeBucket = configurationService.bucketCoordinate(candidate.longitude());
            Instant targetAt = samplingPolicy.truncateToHour(candidate.targetAt());
            WeatherTargetKey key = new WeatherTargetKey(latitudeBucket, longitudeBucket, targetAt);
            batchRows.putIfAbsent(key, new WeatherTargetBatchRow(
                    candidate.latitude(),
                    candidate.longitude(),
                    latitudeBucket,
                    longitudeBucket,
                    targetAt
            ));
        }

        stageStart = metrics().start();
        int created = targetRepository.enqueueMissingBatch(
                userId,
                configurationService.primaryProvider(),
                List.copyOf(batchRows.values()),
                source,
                priority
        );
        metrics().recordStage(stageStart, "enqueue_targets", source.name(), "success");
        return WeatherTargetQueueResponse.builder()
                .targetsCreated(created)
                .targetsAlreadyKnown(Math.max(0, validCandidates - created))
                .targetsSkipped(skipped)
                .build();
    }

    private List<WeatherSampleCandidate> tripCandidates(UUID userId, TimelineTripSlice trip,
                                                        Instant rangeStart, Instant rangeEnd,
                                                        WeatherTargetSource source, int priority) {
        List<WeatherSampleCandidate> result = new ArrayList<>();
        for (Instant targetAt : samplingPolicy.sampleTimesForTrip(
                trip.startTime(), trip.durationSeconds(), rangeStart, rangeEnd)) {
            double[] coordinates = findTripCoordinateAt(userId, trip.startTime(), trip.durationSeconds(), targetAt)
                    .orElseGet(() -> interpolateTripCoordinate(trip, targetAt));
            result.add(new WeatherSampleCandidate(coordinates[0], coordinates[1], targetAt, source, priority));
        }
        return result;
    }

    EnqueueResult enqueueCandidate(UserEntity user, WeatherSampleCandidate candidate) {
        if (!isValidCoordinate(candidate.latitude(), candidate.longitude())) {
            return EnqueueResult.skippedResult();
        }

        double latitudeBucket = configurationService.bucketCoordinate(candidate.latitude());
        double longitudeBucket = configurationService.bucketCoordinate(candidate.longitude());
        Instant targetAt = samplingPolicy.truncateToHour(candidate.targetAt());

        if (sampleRepository.existsAtBucketHour(user.getId(), configurationService.primaryProvider(), latitudeBucket, longitudeBucket, targetAt)) {
            return EnqueueResult.knownResult();
        }

        boolean created = targetRepository.enqueueIfMissing(
                user,
                configurationService.primaryProvider(),
                candidate.latitude(),
                candidate.longitude(),
                latitudeBucket,
                longitudeBucket,
                targetAt,
                candidate.source(),
                candidate.priority()
        );

        return created ? EnqueueResult.createdResult() : EnqueueResult.knownResult();
    }

    Optional<WeatherSampleCandidate> latestActiveCandidate(UserEntity user, Instant now, int intervalMinutes) {
        Optional<TimelineStayEntity> latestStay = stayRepository.find("user.id = ?1 order by timestamp desc", user.getId()).firstResultOptional();
        Optional<TimelineTripEntity> latestTrip = tripRepository.find("user.id = ?1 order by timestamp desc", user.getId()).firstResultOptional();

        TimelineStayEntity stay = latestStay.orElse(null);
        TimelineTripEntity trip = latestTrip.orElse(null);
        Instant stayEnd = stay != null ? stay.getTimestamp().plusSeconds(stay.getStayDuration()) : Instant.EPOCH;
        Instant tripEnd = trip != null ? trip.getTimestamp().plusSeconds(trip.getTripDuration()) : Instant.EPOCH;
        Duration activeWindow = Duration.ofMinutes(Math.max(60, intervalMinutes * 2L));

        if (stayEnd.isAfter(tripEnd) && stay != null && !stayEnd.isBefore(now.minus(activeWindow)) && stay.getLocation() != null) {
            Point point = stay.getLocation();
            return Optional.of(new WeatherSampleCandidate(
                    point.getY(),
                    point.getX(),
                    samplingPolicy.ongoingSampleTime(now, intervalMinutes),
                    WeatherTargetSource.ONGOING,
                    ONGOING_PRIORITY
            ));
        }

        if (trip != null && !tripEnd.isBefore(now.minus(activeWindow))) {
            double[] coordinates = findTripCoordinateAt(user.getId(), trip, now)
                    .orElseGet(() -> interpolateTripCoordinate(trip, now));
            if (coordinates != null) {
                return Optional.of(new WeatherSampleCandidate(
                        coordinates[0],
                        coordinates[1],
                        samplingPolicy.ongoingSampleTime(now, intervalMinutes),
                        WeatherTargetSource.ONGOING,
                        ONGOING_PRIORITY
                ));
            }
        }

        return Optional.empty();
    }

    private Optional<double[]> findTripCoordinateAt(UUID userId, TimelineTripEntity trip, Instant targetAt) {
        return findTripCoordinateAt(userId, trip.getTimestamp(), trip.getTripDuration(), targetAt);
    }

    private Optional<double[]> findTripCoordinateAt(UUID userId, Instant tripStart, long durationSeconds, Instant targetAt) {
        long stageStart = metrics().start();
        Optional<double[]> coordinates = backfillReconciliationRepository.findNearestTripCoordinate(
                userId, tripStart, durationSeconds, targetAt);
        metrics().recordStage(stageStart, "trip_coordinate_lookup", "trip", coordinates.isPresent() ? "success" : "empty");
        return coordinates;
    }

    private double[] interpolateTripCoordinate(TimelineTripEntity trip, Instant targetAt) {
        if (trip == null || trip.getStartPoint() == null || trip.getEndPoint() == null || trip.getTripDuration() <= 0) {
            return null;
        }
        long elapsedSeconds = Math.max(0, Math.min(trip.getTripDuration(), Duration.between(trip.getTimestamp(), targetAt).toSeconds()));
        double ratio = elapsedSeconds / (double) trip.getTripDuration();
        double latitude = trip.getStartPoint().getY() + ((trip.getEndPoint().getY() - trip.getStartPoint().getY()) * ratio);
        double longitude = trip.getStartPoint().getX() + ((trip.getEndPoint().getX() - trip.getStartPoint().getX()) * ratio);
        return new double[]{latitude, longitude};
    }

    private double[] interpolateTripCoordinate(TimelineTripSlice trip, Instant targetAt) {
        long elapsedSeconds = Math.max(0, Math.min(
                trip.durationSeconds(),
                Duration.between(trip.startTime(), targetAt).toSeconds()
        ));
        double ratio = elapsedSeconds / (double) trip.durationSeconds();
        double latitude = trip.startLatitude() + ((trip.endLatitude() - trip.startLatitude()) * ratio);
        double longitude = trip.startLongitude() + ((trip.endLongitude() - trip.startLongitude()) * ratio);
        return new double[]{latitude, longitude};
    }

    private boolean isValidCoordinate(double latitude, double longitude) {
        return latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    record EnqueueResult(boolean created, boolean known, boolean skipped) {
        static EnqueueResult createdResult() {
            return new EnqueueResult(true, false, false);
        }

        static EnqueueResult knownResult() {
            return new EnqueueResult(false, true, false);
        }

        static EnqueueResult skippedResult() {
            return new EnqueueResult(false, false, true);
        }
    }

    private record WeatherTargetKey(double latitudeBucket, double longitudeBucket, Instant targetAt) {
    }

    private record TimelineTripSlice(
            Instant startTime,
            long durationSeconds,
            double startLatitude,
            double startLongitude,
            double endLatitude,
            double endLongitude
    ) {
    }
}
