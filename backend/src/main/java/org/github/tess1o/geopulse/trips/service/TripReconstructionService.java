package org.github.tess1o.geopulse.trips.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.service.AsyncTimelineGenerationService;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionCommitResponseDto;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionPreviewDto;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionRequestDto;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionSegmentDto;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionWaypointDto;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.locationtech.jts.geom.Point;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class TripReconstructionService {

    private static final int TRIP_POINT_INTERVAL_SECONDS = 60;
    private static final int STAY_POINT_INTERVAL_SECONDS = 180;
    private static final int MAX_POINTS_PER_SEGMENT = 5000;
    private static final Pattern JOB_ID_PATTERN = Pattern.compile("Job ID:\\s*([0-9a-fA-F-]{36})");

    private final TripAccessService tripAccessService;
    private final TimelineConfigurationProvider timelineConfigurationProvider;
    private final GpsPointRepository gpsPointRepository;
    private final AsyncTimelineGenerationService asyncTimelineGenerationService;
    private final EntityManager entityManager;

    public TripReconstructionService(TripAccessService tripAccessService,
                                     TimelineConfigurationProvider timelineConfigurationProvider,
                                     GpsPointRepository gpsPointRepository,
                                     AsyncTimelineGenerationService asyncTimelineGenerationService,
                                     EntityManager entityManager) {
        this.tripAccessService = tripAccessService;
        this.timelineConfigurationProvider = timelineConfigurationProvider;
        this.gpsPointRepository = gpsPointRepository;
        this.asyncTimelineGenerationService = asyncTimelineGenerationService;
        this.entityManager = entityManager;
    }

    public TripReconstructionPreviewDto preview(UUID actorUserId,
                                                TripReconstructionRequestDto request) {
        PreparedReconstruction prepared = prepareReconstruction(actorUserId, request);
        long estimatedPoints = generatePoints(prepared.segments(), prepared.timelineConfig()).size();

        return TripReconstructionPreviewDto.builder()
                .estimatedPoints(estimatedPoints)
                .startTime(prepared.startTime())
                .endTime(prepared.endTime())
                .warnings(List.of())
                .build();
    }

    public TripReconstructionCommitResponseDto commit(UUID actorUserId,
                                                      TripReconstructionRequestDto request) {
        PreparedReconstruction prepared = prepareReconstruction(actorUserId, request);
        List<GeneratedPoint> generatedPoints = generatePoints(prepared.segments(), prepared.timelineConfig());

        if (generatedPoints.isEmpty()) {
            throw new IllegalArgumentException("No GPS points were generated from the provided segments");
        }

        PersistenceResult persistenceResult = QuarkusTransaction.requiringNew()
                .call(() -> persistGeneratedPoints(prepared.ownerUserId(), generatedPoints));

        String jobId = null;
        String regenerationWarning = null;
        if (persistenceResult.insertedPoints() > 0 && persistenceResult.earliestInsertedTimestamp() != null) {
            try {
                UUID asyncJobId = asyncTimelineGenerationService.regenerateTimelineFromTimestampAsync(
                        prepared.ownerUserId(),
                        persistenceResult.earliestInsertedTimestamp()
                );
                jobId = asyncJobId.toString();
            } catch (IllegalStateException ex) {
                regenerationWarning = ex.getMessage();
                jobId = extractExistingJobId(ex.getMessage()).orElse(null);
            }
        }

        return TripReconstructionCommitResponseDto.builder()
                .generatedPoints(generatedPoints.size())
                .insertedPoints(persistenceResult.insertedPoints())
                .duplicatePoints(persistenceResult.duplicatePoints())
                .regenerationStartTime(persistenceResult.earliestInsertedTimestamp())
                .jobId(jobId)
                .regenerationWarning(regenerationWarning)
                .build();
    }

    private PersistenceResult persistGeneratedPoints(UUID ownerUserId, List<GeneratedPoint> generatedPoints) {
        UserEntity userRef = entityManager.getReference(UserEntity.class, ownerUserId);
        Instant createdAt = Instant.now();
        long insertedPoints = 0;
        long duplicatePoints = 0;
        Instant earliestInsertedTimestamp = null;

        for (GeneratedPoint point : generatedPoints) {
            Point geometry = GeoUtils.createPoint(point.longitude(), point.latitude());
            Optional<GpsPointEntity> existing = gpsPointRepository.findByUniqueKey(
                    ownerUserId,
                    point.timestamp(),
                    geometry
            );

            if (existing.isPresent()) {
                duplicatePoints++;
                continue;
            }

            GpsPointEntity entity = new GpsPointEntity();
            entity.setUser(userRef);
            entity.setDeviceId("manual-reconstruction");
            entity.setCoordinates(geometry);
            entity.setTimestamp(point.timestamp());
            entity.setVelocity(point.speedKmh());
            entity.setAccuracy(point.accuracy());
            entity.setSourceType(GpsSourceType.MANUAL);
            entity.setCreatedAt(createdAt);
            gpsPointRepository.persist(entity);

            insertedPoints++;
            if (earliestInsertedTimestamp == null || point.timestamp().isBefore(earliestInsertedTimestamp)) {
                earliestInsertedTimestamp = point.timestamp();
            }
        }

        return new PersistenceResult(insertedPoints, duplicatePoints, earliestInsertedTimestamp);
    }

    private PreparedReconstruction prepareReconstruction(UUID actorUserId,
                                                         TripReconstructionRequestDto request) {
        if (request == null || request.getSegments() == null || request.getSegments().isEmpty()) {
            throw new IllegalArgumentException("At least one segment is required");
        }

        Long tripId = request.getTripId();
        if (tripId != null && tripId <= 0) {
            tripId = null;
        }
        TripEntity trip = null;
        UUID ownerUserId;

        if (tripId != null) {
            TripAccessContext access = tripAccessService.requireOwnerAccess(actorUserId, tripId);
            trip = access.trip();
            ownerUserId = access.ownerUserId();

            if (trip.getStartTime() == null || trip.getEndTime() == null) {
                throw new IllegalArgumentException("Trip must have start and end time before reconstruction");
            }
        } else {
            ownerUserId = actorUserId;
        }

        TimelineConfig timelineConfig = timelineConfigurationProvider.getConfigurationForUser(ownerUserId);

        List<NormalizedSegment> normalized = new ArrayList<>();
        int segmentIndex = 0;
        for (TripReconstructionSegmentDto segment : request.getSegments()) {
            normalized.add(normalizeSegment(segment, segmentIndex++));
        }

        normalized.sort(Comparator.comparing(NormalizedSegment::startTime));

        for (int i = 0; i < normalized.size(); i++) {
            NormalizedSegment current = normalized.get(i);

            if (trip != null) {
                if (trip.getStartTime() != null && current.startTime().isBefore(trip.getStartTime())) {
                    throw new IllegalArgumentException("Segment " + (current.index() + 1) + " starts before trip start time");
                }
                if (trip.getEndTime() != null && current.endTime().isAfter(trip.getEndTime())) {
                    throw new IllegalArgumentException("Segment " + (current.index() + 1) + " ends after trip end time");
                }
            }

            if (i == 0) {
                continue;
            }

            NormalizedSegment previous = normalized.get(i - 1);
            if (current.startTime().isBefore(previous.endTime())) {
                throw new IllegalArgumentException(
                        "Segments overlap between positions " + (previous.index() + 1) + " and " + (current.index() + 1)
                );
            }
        }

        Instant startTime = normalized.stream()
                .map(NormalizedSegment::startTime)
                .min(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalArgumentException("Invalid segments"));

        Instant endTime = normalized.stream()
                .map(NormalizedSegment::endTime)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalArgumentException("Invalid segments"));

        return new PreparedReconstruction(ownerUserId, timelineConfig, normalized, startTime, endTime);
    }

    private NormalizedSegment normalizeSegment(TripReconstructionSegmentDto segment, int index) {
        if (segment == null) {
            throw new IllegalArgumentException("Segment " + (index + 1) + " is null");
        }

        if (segment.getStartTime() == null || segment.getEndTime() == null) {
            throw new IllegalArgumentException("Segment " + (index + 1) + " must include startTime and endTime");
        }

        if (!segment.getEndTime().isAfter(segment.getStartTime())) {
            throw new IllegalArgumentException("Segment " + (index + 1) + " endTime must be after startTime");
        }

        String rawType = segment.getSegmentType();
        if (rawType == null || rawType.isBlank()) {
            throw new IllegalArgumentException("Segment " + (index + 1) + " segmentType is required");
        }

        SegmentType type;
        try {
            type = SegmentType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Segment " + (index + 1) + " has invalid segmentType. Allowed values: STAY, TRIP"
            );
        }

        if (type == SegmentType.STAY) {
            if (segment.getLatitude() == null || segment.getLongitude() == null) {
                throw new IllegalArgumentException("Stay segment " + (index + 1) + " requires latitude and longitude");
            }

            return new NormalizedSegment(
                    index,
                    type,
                    segment.getStartTime(),
                    segment.getEndTime(),
                    segment.getLatitude(),
                    segment.getLongitude(),
                    null,
                    List.of()
            );
        }

        List<TripReconstructionWaypointDto> inputWaypoints = segment.getWaypoints() != null
                ? segment.getWaypoints()
                : List.of();
        if (inputWaypoints.size() < 2) {
            throw new IllegalArgumentException("Trip segment " + (index + 1) + " requires at least 2 waypoints");
        }

        List<LatLon> waypoints = new ArrayList<>();
        for (TripReconstructionWaypointDto waypoint : inputWaypoints) {
            if (waypoint == null || waypoint.getLatitude() == null || waypoint.getLongitude() == null) {
                throw new IllegalArgumentException("Trip segment " + (index + 1) + " contains invalid waypoint");
            }
            waypoints.add(new LatLon(waypoint.getLatitude(), waypoint.getLongitude()));
        }

        TripType movementType = parseTripType(segment.getMovementType());

        return new NormalizedSegment(
                index,
                type,
                segment.getStartTime(),
                segment.getEndTime(),
                null,
                null,
                movementType,
                waypoints
        );
    }

    private TripType parseTripType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return TripType.CAR;
        }
        try {
            return TripType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid movementType. Allowed values: WALK, BICYCLE, RUNNING, CAR, TRAIN, FLIGHT, UNKNOWN"
            );
        }
    }

    private List<GeneratedPoint> generatePoints(List<NormalizedSegment> segments, TimelineConfig config) {
        List<GeneratedPoint> allPoints = new ArrayList<>();

        for (NormalizedSegment segment : segments) {
            if (segment.type() == SegmentType.STAY) {
                allPoints.addAll(generateStayPoints(segment));
            } else {
                allPoints.addAll(generateTripPoints(segment, config));
            }
        }

        return allPoints.stream()
                .sorted(Comparator.comparing(GeneratedPoint::timestamp))
                .toList();
    }

    private List<GeneratedPoint> generateStayPoints(NormalizedSegment segment) {
        long durationSeconds = Math.max(1, Duration.between(segment.startTime(), segment.endTime()).getSeconds());
        int pointCount = calculatePointCount(durationSeconds, STAY_POINT_INTERVAL_SECONDS);

        List<GeneratedPoint> points = new ArrayList<>(pointCount);
        for (int i = 0; i < pointCount; i++) {
            double ratio = pointCount <= 1 ? 0d : (double) i / (double) (pointCount - 1);
            Instant timestamp = interpolateInstant(segment.startTime(), segment.endTime(), ratio);
            LatLon jittered = applyDeterministicJitter(segment.latitude(), segment.longitude(), i);

            points.add(new GeneratedPoint(
                    timestamp,
                    jittered.latitude(),
                    jittered.longitude(),
                    i == 0 || i == pointCount - 1 ? 0.0 : 0.8,
                    8.0
            ));
        }

        return points;
    }

    private List<GeneratedPoint> generateTripPoints(NormalizedSegment segment, TimelineConfig config) {
        List<LatLon> waypoints = segment.waypoints();
        long durationSeconds = Math.max(1, Duration.between(segment.startTime(), segment.endTime()).getSeconds());

        List<Double> cumulativeDistances = buildCumulativeDistances(waypoints);
        double totalDistanceMeters = cumulativeDistances.get(cumulativeDistances.size() - 1);
        int pointCount = calculatePointCount(durationSeconds, TRIP_POINT_INTERVAL_SECONDS);

        if (totalDistanceMeters <= 1.0d) {
            List<GeneratedPoint> points = new ArrayList<>(pointCount);
            LatLon anchor = waypoints.get(0);
            for (int i = 0; i < pointCount; i++) {
                double ratio = pointCount <= 1 ? 0d : (double) i / (double) (pointCount - 1);
                Instant timestamp = interpolateInstant(segment.startTime(), segment.endTime(), ratio);
                points.add(new GeneratedPoint(timestamp, anchor.latitude(), anchor.longitude(), 0.0d, 10.0d));
            }
            return points;
        }

        SpeedRange speedRange = resolveSpeedRange(segment.movementType(), config);
        double calculatedAverageKmh = (totalDistanceMeters / 1000.0d) / (durationSeconds / 3600.0d);
        double averageSpeedKmh = clamp(calculatedAverageKmh, speedRange.minKmh(), speedRange.maxKmh());

        List<GeneratedPoint> points = new ArrayList<>(pointCount);
        for (int i = 0; i < pointCount; i++) {
            double ratio = pointCount <= 1 ? 0d : (double) i / (double) (pointCount - 1);
            Instant timestamp = interpolateInstant(segment.startTime(), segment.endTime(), ratio);
            LatLon coordinate = interpolateAlongPolyline(waypoints, cumulativeDistances, totalDistanceMeters * ratio);

            double variationFactor = 0.92d + ((i % 5) * 0.04d);
            double speedKmh = clamp(averageSpeedKmh * variationFactor, speedRange.minKmh(), speedRange.maxKmh());

            points.add(new GeneratedPoint(
                    timestamp,
                    coordinate.latitude(),
                    coordinate.longitude(),
                    speedKmh,
                    10.0
            ));
        }

        return points;
    }

    private List<Double> buildCumulativeDistances(List<LatLon> waypoints) {
        List<Double> cumulative = new ArrayList<>();
        cumulative.add(0.0d);

        double running = 0.0d;
        for (int i = 1; i < waypoints.size(); i++) {
            LatLon previous = waypoints.get(i - 1);
            LatLon current = waypoints.get(i);
            running += GeoUtils.haversine(previous.latitude(), previous.longitude(), current.latitude(), current.longitude());
            cumulative.add(running);
        }

        return cumulative;
    }

    private LatLon interpolateAlongPolyline(List<LatLon> waypoints,
                                            List<Double> cumulativeDistances,
                                            double targetDistanceMeters) {
        if (waypoints.size() == 1) {
            return waypoints.get(0);
        }

        if (targetDistanceMeters <= 0) {
            return waypoints.get(0);
        }

        double totalDistance = cumulativeDistances.get(cumulativeDistances.size() - 1);
        if (totalDistance <= 0) {
            return waypoints.get(0);
        }

        if (targetDistanceMeters >= totalDistance) {
            return waypoints.get(waypoints.size() - 1);
        }

        for (int i = 1; i < cumulativeDistances.size(); i++) {
            double previousDistance = cumulativeDistances.get(i - 1);
            double nextDistance = cumulativeDistances.get(i);
            if (targetDistanceMeters > nextDistance) {
                continue;
            }

            LatLon previous = waypoints.get(i - 1);
            LatLon current = waypoints.get(i);
            double legDistance = Math.max(0.000001d, nextDistance - previousDistance);
            double legRatio = (targetDistanceMeters - previousDistance) / legDistance;

            return new LatLon(
                    interpolate(previous.latitude(), current.latitude(), legRatio),
                    interpolate(previous.longitude(), current.longitude(), legRatio)
            );
        }

        return waypoints.get(waypoints.size() - 1);
    }

    private LatLon applyDeterministicJitter(double latitude, double longitude, int index) {
        double radiusMeters = (index % 4) * 2.0d;
        double angleRadians = Math.toRadians((index * 137.5d) % 360.0d);
        double latOffset = (radiusMeters * Math.cos(angleRadians)) / 111_111.0d;
        double lonDivisor = 111_111.0d * Math.max(0.2d, Math.cos(Math.toRadians(latitude)));
        double lonOffset = (radiusMeters * Math.sin(angleRadians)) / lonDivisor;
        return new LatLon(latitude + latOffset, longitude + lonOffset);
    }

    private int calculatePointCount(long durationSeconds, int intervalSeconds) {
        long safeDuration = Math.max(1L, durationSeconds);
        long base = (safeDuration / intervalSeconds) + 1L;
        return (int) Math.max(2L, Math.min((long) MAX_POINTS_PER_SEGMENT, base));
    }

    private Instant interpolateInstant(Instant start, Instant end, double ratio) {
        long totalMillis = Math.max(1L, Duration.between(start, end).toMillis());
        long offsetMillis = Math.round(totalMillis * ratio);
        return start.plusMillis(offsetMillis);
    }

    private double interpolate(double from, double to, double ratio) {
        return from + ((to - from) * ratio);
    }

    private double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private SpeedRange resolveSpeedRange(TripType movementType, TimelineConfig config) {
        return switch (movementType) {
            case WALK -> new SpeedRange(1.5d, positiveOrDefault(config.getWalkingMaxAvgSpeed(), 6.0d));
            case RUNNING -> new SpeedRange(
                    positiveOrDefault(config.getRunningMinAvgSpeed(), 7.0d),
                    positiveOrDefault(config.getRunningMaxAvgSpeed(), 14.0d)
            );
            case BICYCLE -> new SpeedRange(
                    positiveOrDefault(config.getBicycleMinAvgSpeed(), 8.0d),
                    positiveOrDefault(config.getBicycleMaxAvgSpeed(), 25.0d)
            );
            case TRAIN -> new SpeedRange(
                    positiveOrDefault(config.getTrainMinAvgSpeed(), 30.0d),
                    positiveOrDefault(config.getTrainMaxAvgSpeed(), 150.0d)
            );
            case FLIGHT -> new SpeedRange(
                    positiveOrDefault(config.getFlightMinAvgSpeed(), 400.0d),
                    950.0d
            );
            case UNKNOWN -> new SpeedRange(3.0d, 50.0d);
            case CAR -> {
                double min = positiveOrDefault(config.getCarMinAvgSpeed(), 10.0d);
                double max = positiveOrDefault(config.getTrainMinAvgSpeed(), 120.0d) - 1.0d;
                if (max <= min) {
                    max = min + 80.0d;
                }
                yield new SpeedRange(min, max);
            }
        };
    }

    private Optional<String> extractExistingJobId(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = JOB_ID_PATTERN.matcher(message);
        if (!matcher.find()) {
            return Optional.empty();
        }

        String candidate = matcher.group(1);
        try {
            UUID.fromString(candidate);
            return Optional.of(candidate);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private double positiveOrDefault(Double value, double fallback) {
        return (value != null && value > 0) ? value : fallback;
    }

    private enum SegmentType {
        STAY,
        TRIP
    }

    private record LatLon(double latitude, double longitude) {
    }

    private record SpeedRange(double minKmh, double maxKmh) {
    }

    private record GeneratedPoint(Instant timestamp,
                                  double latitude,
                                  double longitude,
                                  double speedKmh,
                                  double accuracy) {
    }

    private record NormalizedSegment(int index,
                                     SegmentType type,
                                     Instant startTime,
                                     Instant endTime,
                                     Double latitude,
                                     Double longitude,
                                     TripType movementType,
                                     List<LatLon> waypoints) {
    }

    private record PreparedReconstruction(UUID ownerUserId,
                                          TimelineConfig timelineConfig,
                                          List<NormalizedSegment> segments,
                                          Instant startTime,
                                          Instant endTime) {
    }

    private record PersistenceResult(long insertedPoints,
                                     long duplicatePoints,
                                     Instant earliestInsertedTimestamp) {
    }
}
