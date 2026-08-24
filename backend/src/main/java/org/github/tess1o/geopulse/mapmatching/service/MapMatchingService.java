package org.github.tess1o.geopulse.mapmatching.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.mapmatching.dto.*;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingStatus;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingSource;
import org.github.tess1o.geopulse.mapmatching.model.TimelineTripPathMatchEntity;
import org.github.tess1o.geopulse.mapmatching.repository.TimelineTripPathMatchRepository;
import org.github.tess1o.geopulse.prometheus.GeoPulseWorkloadMetrics;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.util.TimelineGpsAccuracyFilter;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.time.Instant;
import java.util.*;

@Slf4j
@ApplicationScoped
public class MapMatchingService {

    private static final TypeReference<List<List<MapMatchedPointDTO>>> SEGMENTS_TYPE = new TypeReference<>() {
    };
    private final MapMatchingConfiguration configuration;
    private final TimelineTripPathMatchRepository matchRepository;
    private final TimelineTripRepository tripRepository;
    private final GpsPointRepository gpsPointRepository;
    private final TimelineConfigurationProvider timelineConfigurationProvider;
    private final MapMatchingHashService hashService;
    private final MapMatchingProfileResolver profileResolver;
    private final ValhallaMapMatchingProvider valhallaProvider;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Inject
    GeoPulseWorkloadMetrics workloadMetrics;

    public MapMatchingService(MapMatchingConfiguration configuration,
                              TimelineTripPathMatchRepository matchRepository,
                              TimelineTripRepository tripRepository,
                              GpsPointRepository gpsPointRepository,
                              TimelineConfigurationProvider timelineConfigurationProvider,
                              MapMatchingHashService hashService,
                              MapMatchingProfileResolver profileResolver,
                              ValhallaMapMatchingProvider valhallaProvider,
                              UserRepository userRepository,
                              ObjectMapper objectMapper) {
        this.configuration = configuration;
        this.matchRepository = matchRepository;
        this.tripRepository = tripRepository;
        this.gpsPointRepository = gpsPointRepository;
        this.timelineConfigurationProvider = timelineConfigurationProvider;
        this.hashService = hashService;
        this.profileResolver = profileResolver;
        this.valhallaProvider = valhallaProvider;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public MapMatchingResolutionResponse resolve(UUID userId, List<Long> tripIds) {
        if (!isUserEnabled(userId)) {
            return MapMatchingResolutionResponse.builder()
                    .enabled(false)
                    .provider(configuration.provider())
                    .trips(List.of())
                    .build();
        }

        List<Long> normalizedTripIds = tripIds == null ? List.of() : tripIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedTripIds.size() > 100) {
            throw new IllegalArgumentException("At most 100 trip IDs may be resolved at once");
        }
        if (normalizedTripIds.isEmpty()) {
            return MapMatchingResolutionResponse.builder()
                    .enabled(true)
                    .provider(configuration.provider())
                    .trips(List.of())
                    .build();
        }

        Double maxAccuracy = activeMaxAccuracy(userId);
        UserEntity user = userRepository.findById(userId);
        List<MapMatchingTripResolutionDTO> resolutions = new ArrayList<>();
        for (Long tripId : normalizedTripIds) {
            tripRepository.findByIdOptional(tripId)
                    .filter(trip -> trip.getUser() != null && userId.equals(trip.getUser().getId()))
                    .ifPresent(trip -> resolutions.add(resolveTrip(
                            userId, user, trip, MapMatchingSource.ON_DEMAND, true, maxAccuracy)));
        }
        return MapMatchingResolutionResponse.builder()
                .enabled(true)
                .provider(configuration.provider())
                .trips(resolutions)
                .build();
    }

    void enqueueBackgroundTrips(UUID userId, List<TimelineTripEntity> trips, MapMatchingSource source,
                                Map<UUID, Double> maxAccuracyByUser) {
        if (!configuration.isEnabled() || trips == null || trips.isEmpty()) {
            return;
        }
        Double maxAccuracy = cachedActiveMaxAccuracy(userId, maxAccuracyByUser);
        UserEntity user = userRepository.findById(userId);
        for (TimelineTripEntity trip : trips) {
            resolveTrip(userId, user, trip, source, false, maxAccuracy);
        }
    }

    public List<MapMatchingTripResolutionDTO> status(UUID userId, List<Long> targetIds) {
        List<Long> normalized = targetIds == null ? List.of() : targetIds.stream()
                .filter(Objects::nonNull).distinct().toList();
        if (normalized.size() > 100) {
            throw new IllegalArgumentException("At most 100 target IDs may be checked at once");
        }
        return matchRepository.findOwnedTargets(userId, normalized).stream()
                .map(target -> resolutionForTarget(
                        target.getTrip() == null ? null : target.getTrip().getId(), target))
                .toList();
    }

    int processTargets(List<TimelineTripPathMatchEntity> targets, Map<UUID, Double> maxAccuracyByUser) {
        int processed = 0;
        for (TimelineTripPathMatchEntity target : targets) {
            if (target == null || target.getTrip() == null || target.getTrip().getUser() == null) continue;
            UUID userId = target.getTrip().getUser().getId();
            processTarget(target, cachedActiveMaxAccuracy(userId, maxAccuracyByUser));
            processed++;
        }
        return processed;
    }

    private void processTarget(TimelineTripPathMatchEntity target, Double maxAccuracy) {
        if (target == null || target.getId() == null || target.getTrip() == null) {
            return;
        }
        long targetId = target.getId();
        matchRepository.markAttemptStarted(targetId);
        try {
            TimelineTripEntity trip = target.getTrip();
            List<GpsPointEntity> points = loadEligiblePoints(trip.getUser().getId(), trip, maxAccuracy);
            if (points.size() < 2) {
                matchRepository.markSkipped(targetId, "Trip has fewer than two eligible GPS points");
                recordOutcome(target, "skipped");
                return;
            }
            if (trip.getTripDuration() > Math.max(1, configuration.getMaxTripDurationHours()) * 3600L) {
                matchRepository.markSkipped(targetId, "Trip exceeds configured map-matching duration limit");
                recordOutcome(target, "skipped");
                return;
            }

            MapMatchingProvider provider = providerFor(target.getProvider());
            List<List<MapMatchedPointDTO>> matchedSegments = new ArrayList<>();
            List<List<GpsPointEntity>> chunks = chunkPoints(points, Math.max(2, configuration.getMaxInputPoints()));
            if (chunks.isEmpty()) {
                matchRepository.markSkipped(targetId, "Trip has no contiguous GPS segment with at least two points");
                recordOutcome(target, "skipped");
                return;
            }
            for (List<GpsPointEntity> chunk : chunks) {
                List<List<MapMatchedPointDTO>> chunkSegments = provider.matchSegments(chunk, target.getProfile())
                        .stream()
                        .filter(segment -> segment != null && segment.size() >= 2)
                        .toList();
                if (chunkSegments.isEmpty()) {
                    matchRepository.markSkipped(targetId, "Valhalla returned fewer than two matched points");
                    recordOutcome(target, "skipped");
                    return;
                }
                if (!hasAcceptableMatchedCoverage(chunk, chunkSegments)) {
                    matchRepository.markSkipped(targetId,
                            "Valhalla matched only a disconnected or partial route; retaining raw GPS path");
                    recordOutcome(target, "skipped");
                    return;
                }
                matchedSegments.addAll(chunkSegments);
            }
            String json = objectMapper.writeValueAsString(matchedSegments);
            matchRepository.markMatched(targetId, json);
            recordOutcome(target, "completed");
        } catch (Exception e) {
            log.warn("Failed to map-match target {}: {}", targetId, e.getMessage());
            if (isRetryableFailure(e)) {
                matchRepository.markFailedOrRetry(targetId, e.getMessage(), configuration.getMaxAttempts());
                recordOutcome(target, "failed_or_retrying");
            } else {
                matchRepository.markFailed(targetId, e.getMessage());
                recordOutcome(target, "failed");
            }
        }
    }

    boolean isRetryableFailure(Throwable error) {
        return !(error instanceof ValhallaHttpException valhallaError) || valhallaError.isRetryable();
    }

    public void resetStaleProcessing() {
        long recovered = matchRepository.resetStaleProcessing(Instant.now().minusSeconds(15 * 60L));
        if (recovered > 0 && workloadMetrics != null) {
            workloadMetrics.increment("geopulse.map_matching.retry.recovered", recovered,
                    "component", "map_matching");
        }
    }

    private MapMatchingTripResolutionDTO resolveTrip(UUID userId, UserEntity user, TimelineTripEntity trip,
                                                     MapMatchingSource source, boolean requireDisplayPreference,
                                                     Double maxAccuracy) {
        if (!configuration.isEnabled()) {
            return status(trip.getId(), "UNAVAILABLE", null, null, null);
        }
        if (!"valhalla".equals(configuration.provider()) || !configuration.valhallaConfigured()) {
            return status(trip.getId(), "UNAVAILABLE", null, "Valhalla is not configured", null);
        }

        if (requireDisplayPreference && (user == null
                || !Boolean.TRUE.equals(user.getTimelineDisplayMapMatchingEnabled()))) {
            return status(trip.getId(), "UNAVAILABLE", null, null, null);
        }
        if (trip.getTripDuration() > Math.max(1, configuration.getMaxTripDurationHours()) * 3600L) {
            return status(trip.getId(), "SKIPPED", null, "Trip exceeds configured map-matching duration limit", null);
        }

        List<GpsPointEntity> points = loadEligiblePoints(userId, trip, maxAccuracy);
        if (points.size() < 2) {
            return status(trip.getId(), "SKIPPED", null, "Trip has fewer than two eligible GPS points", null);
        }

        String profile = profileResolver.resolveProfile(trip.getMovementType());
        if (profile == null) {
            return status(trip.getId(), "SKIPPED", null,
                    "Movement type is not supported by road/path map matching", null);
        }
        String configHash = hashService.configHash(configuration.configHashSource() + "|" + profile);
        String inputHash = hashService.inputHash(points, maxAccuracy);
        Optional<TimelineTripPathMatchEntity> existing = matchRepository.findCurrent(
                userId, configuration.provider(), profile, configHash, inputHash);

        if (existing.isPresent()) {
            TimelineTripPathMatchEntity target = existing.get();
            matchRepository.attachToTrip(target, trip, source);
            return resolutionForTarget(trip.getId(), target);
        }

        TimelineTripPathMatchEntity target = matchRepository.enqueueIfMissing(
                user, trip, configuration.provider(), profile, configHash, inputHash, source);
        return status(trip.getId(), "QUEUED", target.getId(), null, null);
    }

    private boolean isDisplayEnabled(UUID userId) {
        UserEntity user = userRepository.findById(userId);
        return user != null && Boolean.TRUE.equals(user.getTimelineDisplayMapMatchingEnabled());
    }

    private boolean isUserEnabled(UUID userId) {
        return configuration.isEnabled() && isDisplayEnabled(userId);
    }

    private List<GpsPointEntity> loadEligiblePoints(UUID userId, TimelineTripEntity trip, Double maxAccuracy) {
        Instant start = trip.getTimestamp();
        Instant end = start.plusSeconds(Math.max(0L, trip.getTripDuration()));
        return gpsPointRepository.findEligibleByUserIdAndTimePeriod(userId, start, end, maxAccuracy);
    }

    private Double cachedActiveMaxAccuracy(UUID userId, Map<UUID, Double> cache) {
        if (cache.containsKey(userId)) return cache.get(userId);
        Double maxAccuracy = activeMaxAccuracy(userId);
        cache.put(userId, maxAccuracy);
        return maxAccuracy;
    }

    private Double activeMaxAccuracy(UUID userId) {
        TimelineConfig config = timelineConfigurationProvider.getConfigurationForUser(userId);
        return TimelineGpsAccuracyFilter.getActiveMaxAccuracyThreshold(config);
    }

    List<List<GpsPointEntity>> chunkPoints(List<GpsPointEntity> points, int maxPoints) {
        List<List<GpsPointEntity>> chunks = new ArrayList<>();
        List<GpsPointEntity> current = new ArrayList<>(maxPoints);
        for (GpsPointEntity point : points) {
            GpsPointEntity previous = current.isEmpty() ? null : current.getLast();
            boolean gap = previous != null && previous.getTimestamp() != null && point.getTimestamp() != null
                    && point.getTimestamp().isAfter(previous.getTimestamp().plusSeconds(10 * 60L));
            if (gap) {
                if (current.size() >= 2) chunks.add(List.copyOf(current));
                current.clear();
            } else if (current.size() >= maxPoints) {
                GpsPointEntity overlap = current.getLast();
                chunks.add(List.copyOf(current));
                current.clear();
                current.add(overlap);
            }
            current.add(point);
        }
        if (current.size() >= 2) chunks.add(List.copyOf(current));
        return chunks;
    }

    boolean hasAcceptableMatchedCoverage(List<GpsPointEntity> inputPoints,
                                         List<List<MapMatchedPointDTO>> matchedSegments) {
        double rawDistanceMeters = inputDistanceMeters(inputPoints);
        if (rawDistanceMeters < Math.max(1, configuration.getQualityMinRawDistanceMeters())) {
            return true;
        }

        double matchedDistanceMeters = matchedDistanceMeters(matchedSegments);
        if (matchedDistanceMeters <= 0) {
            return false;
        }

        double coveragePercent = matchedDistanceMeters * 100.0 / rawDistanceMeters;
        if (coveragePercent < Math.max(1, configuration.getQualityMinDistanceCoveragePercent())) {
            return false;
        }

        double discontinuityMeters = matchedDiscontinuityMeters(matchedSegments);
        double maxAllowedDiscontinuity = Math.max(
                Math.max(1, configuration.getQualityMaxShortDiscontinuityMeters()),
                rawDistanceMeters * Math.max(1, configuration.getQualityMaxDiscontinuityPercent()) / 100.0);
        return discontinuityMeters <= maxAllowedDiscontinuity;
    }

    private double inputDistanceMeters(List<GpsPointEntity> points) {
        if (points == null || points.size() < 2) {
            return 0.0;
        }

        double distance = 0.0;
        GpsPointEntity previous = null;
        for (GpsPointEntity point : points) {
            if (point == null || point.getCoordinates() == null) {
                continue;
            }
            if (previous != null && previous.getCoordinates() != null) {
                distance += GeoUtils.haversine(
                        previous.getCoordinates().getY(), previous.getCoordinates().getX(),
                        point.getCoordinates().getY(), point.getCoordinates().getX());
            }
            previous = point;
        }
        return distance;
    }

    private double matchedDistanceMeters(List<List<MapMatchedPointDTO>> segments) {
        if (segments == null) {
            return 0.0;
        }

        double distance = 0.0;
        for (List<MapMatchedPointDTO> segment : segments) {
            if (segment == null || segment.size() < 2) {
                continue;
            }
            MapMatchedPointDTO previous = null;
            for (MapMatchedPointDTO point : segment) {
                if (!valid(point)) {
                    continue;
                }
                if (previous != null) {
                    distance += GeoUtils.haversine(
                            previous.getLatitude(), previous.getLongitude(),
                            point.getLatitude(), point.getLongitude());
                }
                previous = point;
            }
        }
        return distance;
    }

    private double matchedDiscontinuityMeters(List<List<MapMatchedPointDTO>> segments) {
        if (segments == null || segments.size() < 2) {
            return 0.0;
        }

        double distance = 0.0;
        MapMatchedPointDTO previousEnd = null;
        for (List<MapMatchedPointDTO> segment : segments) {
            MapMatchedPointDTO first = firstValid(segment);
            MapMatchedPointDTO last = lastValid(segment);
            if (first == null || last == null) {
                continue;
            }
            if (previousEnd != null) {
                distance += GeoUtils.haversine(
                        previousEnd.getLatitude(), previousEnd.getLongitude(),
                        first.getLatitude(), first.getLongitude());
            }
            previousEnd = last;
        }
        return distance;
    }

    private MapMatchedPointDTO firstValid(List<MapMatchedPointDTO> segment) {
        if (segment == null) {
            return null;
        }
        return segment.stream()
                .filter(this::valid)
                .findFirst()
                .orElse(null);
    }

    private MapMatchedPointDTO lastValid(List<MapMatchedPointDTO> segment) {
        if (segment == null) {
            return null;
        }
        for (int i = segment.size() - 1; i >= 0; i--) {
            MapMatchedPointDTO point = segment.get(i);
            if (valid(point)) {
                return point;
            }
        }
        return null;
    }

    private boolean valid(MapMatchedPointDTO point) {
        return point != null
                && Double.isFinite(point.getLatitude())
                && Double.isFinite(point.getLongitude());
    }

    private MapMatchingProvider providerFor(String provider) {
        if ("valhalla".equalsIgnoreCase(provider)) {
            return valhallaProvider;
        }
        throw new IllegalArgumentException("Unsupported map-matching provider: " + provider);
    }

    private List<List<MapMatchedPointDTO>> readSegments(TimelineTripPathMatchEntity target) {
        if (target.getMatchedSegmentsJson() == null || target.getMatchedSegmentsJson().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(target.getMatchedSegmentsJson(), SEGMENTS_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse cached map-match segments for target {}", target.getId(), e);
            return List.of();
        }
    }

    private MapMatchingTripResolutionDTO status(Long tripId,
                                                String status,
                                                Long targetId,
                                                String error,
                                                List<List<MapMatchedPointDTO>> segments) {
        return MapMatchingTripResolutionDTO.builder()
                .tripId(tripId)
                .status(status)
                .targetId(targetId)
                .error(error)
                .segments(segments)
                .build();
    }

    private MapMatchingTripResolutionDTO resolutionForTarget(Long tripId, TimelineTripPathMatchEntity target) {
        List<List<MapMatchedPointDTO>> segments = target.getStatus() == MapMatchingStatus.MATCHED
                ? readSegments(target) : null;
        return MapMatchingTripResolutionDTO.builder()
                .tripId(tripId)
                .status(toExternalStatus(target.getStatus()))
                .targetId(target.getId())
                .error(target.getLastError())
                .source(target.getSource())
                .retryAt(target.getStatus() == MapMatchingStatus.PENDING ? target.getNextAttemptAt() : null)
                .pollAfterMs(recommendedPollDelay(target))
                .segments(segments)
                .build();
    }

    private int recommendedPollDelay(TimelineTripPathMatchEntity target) {
        if (target.getStatus() == MapMatchingStatus.PROCESSING) return 2500;
        if (target.getStatus() != MapMatchingStatus.PENDING || target.getNextAttemptAt() == null) return 0;
        long untilRetryMs = target.getNextAttemptAt().toEpochMilli() - Instant.now().toEpochMilli();
        return (int) Math.min(60_000L, Math.max(5_000L, untilRetryMs));
    }

    private String toExternalStatus(MapMatchingStatus status) {
        if (status == MapMatchingStatus.PENDING) {
            return "QUEUED";
        }
        if (status == MapMatchingStatus.MATCHED) {
            return "COMPLETED";
        }
        return status == null ? "UNAVAILABLE" : status.name();
    }

    private void recordOutcome(TimelineTripPathMatchEntity target, String result) {
        if (workloadMetrics != null) {
            workloadMetrics.increment("geopulse.map_matching.targets",
                    "component", "map_matching",
                    "source", target.getSource() == null ? "UNKNOWN" : target.getSource(),
                    "result", result);
        }
    }
}
