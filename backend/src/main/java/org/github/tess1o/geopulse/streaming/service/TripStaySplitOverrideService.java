package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.notes.service.TimelineNoteService;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.service.LocationPointResolver;
import org.github.tess1o.geopulse.shared.service.LocationResolutionResult;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.engine.TimelineEventFinalizationService;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.domain.UserState;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TripStaySplitRequest;
import org.github.tess1o.geopulse.streaming.model.dto.TripStaySplitResponse;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripStaySplitOverrideEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripMovementOverrideRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripStaySplitOverrideRepository;
import org.github.tess1o.geopulse.streaming.service.converters.StreamingTimelineConverter;
import org.github.tess1o.geopulse.streaming.service.trips.TravelClassification;
import org.github.tess1o.geopulse.streaming.service.trips.TripWaterClassificationService;
import org.github.tess1o.geopulse.streaming.service.trips.TripWaterStatistics;
import org.github.tess1o.geopulse.streaming.util.TimelineGpsAccuracyFilter;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.locationtech.jts.geom.Point;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class TripStaySplitOverrideService {

    private static final long MANUAL_SPLIT_ANCHOR_MAX_DELTA_SECONDS = 300;

    @ConfigProperty(name = "geopulse.timeline.trip_stay_split.matching.max_timestamp_delta_seconds",
            defaultValue = "2700")
    long maxTimestampDeltaSeconds;

    @ConfigProperty(name = "geopulse.timeline.trip_stay_split.matching.max_point_distance_meters",
            defaultValue = "350.0")
    double maxPointDistanceMeters;

    @ConfigProperty(name = "geopulse.timeline.trip_stay_split.matching.min_duration_ratio",
            defaultValue = "0.6")
    double minDurationRatio;

    @ConfigProperty(name = "geopulse.timeline.trip_stay_split.matching.max_duration_ratio",
            defaultValue = "1.8")
    double maxDurationRatio;

    @ConfigProperty(name = "geopulse.timeline.trip_stay_split.matching.min_distance_ratio",
            defaultValue = "0.6")
    double minDistanceRatio;

    @ConfigProperty(name = "geopulse.timeline.trip_stay_split.matching.max_distance_ratio",
            defaultValue = "1.8")
    double maxDistanceRatio;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    TimelineDataGapRepository dataGapRepository;

    @Inject
    TimelineTripStaySplitOverrideRepository overrideRepository;

    @Inject
    TimelineTripMovementOverrideRepository movementOverrideRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    TimelineConfigurationProvider configProvider;

    @Inject
    TimelineEventFinalizationService finalizationService;

    @Inject
    LocationPointResolver locationPointResolver;

    @Inject
    StreamingTimelineConverter converter;

    @Inject
    TripWaterClassificationService tripWaterClassificationService;

    @Inject
    TravelClassification travelClassification;

    @Inject
    TimelineNoteService timelineNoteService;

    @Inject
    EntityManager entityManager;

    @Transactional
    public Optional<TripStaySplitResponse> previewSplit(UUID userId, Long tripId, TripStaySplitRequest request) {
        Optional<TimelineTripEntity> tripOptional = findTripOwnedByUser(userId, tripId);
        if (tripOptional.isEmpty()) {
            return Optional.empty();
        }

        TimelineTripEntity trip = tripOptional.get();
        PreparedSplit split = prepareSplit(userId, trip, request);
        return Optional.of(toResponse(null, trip.getId(), split, null));
    }

    @Transactional
    public Optional<TripStaySplitResponse> splitTrip(UUID userId, Long tripId, TripStaySplitRequest request) {
        Optional<TimelineTripEntity> tripOptional = findTripOwnedByUser(userId, tripId);
        if (tripOptional.isEmpty()) {
            return Optional.empty();
        }

        TimelineTripEntity trip = tripOptional.get();
        PreparedSplit split = prepareSplit(userId, trip, request);
        TimelineTripStaySplitOverrideEntity override = upsertOverride(userId, trip, split.location());

        applySplit(trip, split, override);
        timelineNoteService.reattachAnchoredNotes(userId);

        return Optional.of(toResponse(override, tripId, split, null));
    }

    @Transactional
    public Optional<TripStaySplitResponse> removeManualOverride(UUID userId, Long overrideId) {
        Optional<TimelineTripStaySplitOverrideEntity> overrideOptional = overrideRepository.findByIdAndUserId(overrideId, userId);
        if (overrideOptional.isEmpty()) {
            return Optional.empty();
        }

        TimelineTripStaySplitOverrideEntity override = overrideOptional.get();
        Instant regenerationStartTime = override.getSourceTripTimestamp();
        overrideRepository.delete(override);

        return Optional.of(TripStaySplitResponse.builder()
                .overrideId(overrideId)
                .regenerationStartTime(regenerationStartTime)
                .build());
    }

    @Transactional
    public int reapplyManualOverrides(UUID userId) {
        List<TimelineTripStaySplitOverrideEntity> overrides = overrideRepository.findByUserId(userId);
        if (overrides.isEmpty()) {
            return 0;
        }

        Set<Long> matchedTripIds = new HashSet<>();
        int appliedCount = 0;
        for (TimelineTripStaySplitOverrideEntity override : overrides) {
            TimelineTripEntity trip = findBestCoveringTrip(userId, override, matchedTripIds);
            if (trip == null) {
                continue;
            }

            try {
                PreparedSplit split = prepareSplit(userId, trip, override);
                applySplit(trip, split, override);
                if (trip.getId() != null) {
                    matchedTripIds.add(trip.getId());
                }
                appliedCount++;
            } catch (RuntimeException ex) {
                log.warn("Skipping Trip -> Stay -> Trip override {} for user {}: {}",
                        override.getId(), userId, ex.getMessage());
            }
        }

        if (appliedCount > 0) {
            log.info("Re-applied {} manual Trip -> Stay -> Trip overrides for user {}", appliedCount, userId);
        }
        return appliedCount;
    }

    private Optional<TimelineTripEntity> findTripOwnedByUser(UUID userId, Long tripId) {
        return tripRepository.findByIdOptional(tripId)
                .filter(trip -> trip.getUser() != null && userId.equals(trip.getUser().getId()));
    }

    private Instant resolveOverrideAnchorTimestamp(TimelineTripStaySplitOverrideEntity override) {
        if (override.getAnchorTimestamp() != null) {
            return override.getAnchorTimestamp();
        }
        return override.getStayStartTime().plus(Duration.between(override.getStayStartTime(), override.getStayEndTime()).dividedBy(2));
    }

    private TimelineTripStaySplitOverrideEntity upsertOverride(UUID userId,
                                                               TimelineTripEntity trip,
                                                               ResolvedLocation location) {
        Instant stayStart = location.stayStartTime();
        Instant stayEnd = location.stayEndTime();
        TimelineTripStaySplitOverrideEntity override = overrideRepository
                .findByUserIdAndSourceTripAndStay(userId, trip.getTimestamp(), stayStart, stayEnd)
                .orElseGet(() -> TimelineTripStaySplitOverrideEntity.builder()
                        .user(entityManager.getReference(UserEntity.class, userId))
                        .build());

        if (override.getId() == null) {
            override.setSourceTripTimestamp(trip.getTimestamp());
            override.setSourceTripDurationSeconds(trip.getTripDuration());
            override.setSourceDistanceMeters(trip.getDistanceMeters());
            override.setSourceStartLatitude(trip.getStartPoint().getY());
            override.setSourceStartLongitude(trip.getStartPoint().getX());
            override.setSourceEndLatitude(trip.getEndPoint().getY());
            override.setSourceEndLongitude(trip.getEndPoint().getX());
        }

        syncLocation(override, location);

        if (override.getId() == null) {
            overrideRepository.persist(override);
        }
        return override;
    }

    private PreparedSplit prepareSplit(UUID userId,
                                       TimelineTripEntity trip,
                                       TripStaySplitRequest request) {
        validateRequest(request);
        ResolvedLocation location = resolveLocation(userId,
                request.getStayStartTime(),
                request.getStayEndTime(),
                request.getAnchorTimestamp(),
                request.getLatitude(),
                request.getLongitude(),
                request.getLocationName());
        return prepareSplit(userId, trip, location);
    }

    private PreparedSplit prepareSplit(UUID userId,
                                       TimelineTripEntity trip,
                                       TimelineTripStaySplitOverrideEntity override) {
        ResolvedLocation location = new ResolvedLocation(
                override.getStayStartTime(),
                override.getStayEndTime(),
                resolveOverrideAnchorTimestamp(override),
                override.getStayLatitude(),
                override.getStayLongitude(),
                override.getStayLocationName(),
                override.getFavoriteLocation() != null ? override.getFavoriteLocation().getId() : null,
                override.getGeocodingLocation() != null ? override.getGeocodingLocation().getId() : null,
                override.getStayLocationSource()
        );
        return prepareSplit(userId, trip, location);
    }

    private PreparedSplit prepareSplit(UUID userId,
                                       TimelineTripEntity trip,
                                       ResolvedLocation location) {
        ensureInsideTrip(trip, location.stayStartTime(), location.stayEndTime());

        TimelineConfig config = configProvider.getConfigurationForUser(userId);
        if (Duration.between(location.stayStartTime(), location.stayEndTime()).getSeconds() < 60) {
            throw new IllegalArgumentException("Stay duration must be at least 60 seconds");
        }

        ensureTimeAnchoredStayLocation(userId, trip, location, config);
        ensureNoTimelineOverlap(userId, location.stayStartTime(), location.stayEndTime());

        TimelineTripEntity firstTrip = buildTrip(userId, trip.getTimestamp(), location.stayStartTime(), location, true, config);
        TimelineTripEntity secondTrip = buildTrip(userId, location.stayEndTime(), tripEnd(trip), location, false, config);
        TimelineStayEntity stay = buildStay(userId, location);

        return new PreparedSplit(firstTrip, stay, secondTrip, location);
    }

    private void applySplit(TimelineTripEntity originalTrip,
                            PreparedSplit split,
                            TimelineTripStaySplitOverrideEntity override) {
        Long originalTripId = originalTrip.getId();
        if (originalTripId != null) {
            movementOverrideRepository.deleteByUserIdAndTripId(originalTrip.getUser().getId(), originalTripId);
        }

        tripRepository.delete(originalTrip);
        tripRepository.persist(split.firstTrip());
        stayRepository.persist(split.stay());
        tripRepository.persist(split.secondTrip());

        syncLocation(override, split.location());
        override.setStay(split.stay());
        entityManager.flush();
    }

    private TimelineTripEntity buildTrip(UUID userId,
                                         Instant start,
                                         Instant end,
                                         ResolvedLocation location,
                                         boolean firstLeg,
                                         TimelineConfig config) {
        List<GPSPoint> points = gpsPointRepository.findEssentialPointsInInterval(userId, start, end);
        if (firstLeg) {
            if (points.isEmpty() || points.getFirst().getTimestamp().isAfter(start)) {
                throw new IllegalArgumentException("First trip leg has no GPS points");
            }
            points = new java.util.ArrayList<>(points);
            points.add(new GPSPoint(location.stayStartTime(), location.latitude(), location.longitude(), 0, 0));
        } else {
            if (points.isEmpty() || points.getLast().getTimestamp().isBefore(end)) {
                throw new IllegalArgumentException("Second trip leg has no GPS points");
            }
            points = new java.util.ArrayList<>(points);
            points.addFirst(new GPSPoint(location.stayEndTime(), location.latitude(), location.longitude(), 0, 0));
        }

        UserState state = new UserState();
        points.forEach(state::addActivePoint);
        Trip trip = finalizationService.finalizeTrip(state, config);
        if (trip == null) {
            throw new IllegalArgumentException("Unable to build trip leg from GPS points");
        }

        TimelineTripEntity entity = converter.convertStreamingTripToEntity(
                trip,
                entityManager.getReference(UserEntity.class, userId)
        );
        applyWaterClassification(userId, entity, config);
        return entity;
    }

    private void applyWaterClassification(UUID userId, TimelineTripEntity trip, TimelineConfig config) {
        TripWaterStatistics water = tripWaterClassificationService.calculateStatistics(
                userId,
                trip.getTimestamp(),
                trip.getTimestamp().plusSeconds(trip.getTripDuration()),
                config
        );
        if (water != null && water.hasEvidence()) {
            trip.setWaterDistanceMeters(water.waterDistanceMeters());
            trip.setWaterDistanceRatio(water.waterDistanceRatio());
            trip.setLongestWaterSegmentMeters(water.longestWaterSegmentMeters());
            trip.setWaterSampleCount(water.waterSampleCount());
            trip.setWaterEvidenceAvailable(water.evidenceAvailable());
            trip.setMovementType(travelClassification.classifyTravelType(trip, config).name());
        }
    }

    private TimelineStayEntity buildStay(UUID userId, ResolvedLocation location) {
        TimelineStayEntity stay = TimelineStayEntity.builder()
                .user(entityManager.getReference(UserEntity.class, userId))
                .timestamp(location.stayStartTime())
                .stayDuration(Duration.between(location.stayStartTime(), location.stayEndTime()).getSeconds())
                .location(GeoUtils.createPoint(location.longitude(), location.latitude()))
                .locationName(normalizeLocationName(location.locationName()))
                .locationSource(location.locationSource())
                .build();

        if (location.favoriteId() != null) {
            stay.setFavoriteLocation(entityManager.getReference(FavoritesEntity.class, location.favoriteId()));
        }
        if (location.geocodingId() != null) {
            stay.setGeocodingLocation(entityManager.getReference(ReverseGeocodingLocationEntity.class, location.geocodingId()));
        }
        return stay;
    }

    private ResolvedLocation resolveLocation(UUID userId,
                                             Instant stayStartTime,
                                             Instant stayEndTime,
                                             Instant anchorTimestamp,
                                             Double latitude,
                                             Double longitude,
                                             String requestedLocationName) {
        Point point = GeoUtils.createPoint(longitude, latitude);
        LocationResolutionResult result = locationPointResolver.resolveLocationWithReferences(userId, point);

        LocationSource source = result.getFavoriteId() != null
                ? LocationSource.FAVORITE
                : result.getGeocodingId() != null
                ? LocationSource.GEOCODING
                : LocationSource.HISTORICAL;

        String locationName = trimToNull(requestedLocationName);
        if (locationName == null) {
            locationName = normalizeLocationName(result.getLocationName());
        }

        return new ResolvedLocation(
                stayStartTime,
                stayEndTime,
                anchorTimestamp,
                point.getY(),
                point.getX(),
                locationName,
                result.getFavoriteId(),
                result.getGeocodingId(),
                source
        );
    }

    private TimelineTripEntity findBestCoveringTrip(UUID userId,
                                                    TimelineTripStaySplitOverrideEntity override,
                                                    Set<Long> matchedTripIds) {
        List<TimelineTripEntity> candidates = tripRepository.find("""
                        user.id = ?1
                        and timestamp < ?2
                        and FUNCTION('TIMESTAMPADD', SECOND, tripDuration, timestamp) > ?3
                        order by timestamp
                        """,
                userId,
                override.getStayStartTime(),
                override.getStayEndTime()).list();

        TimelineTripEntity bestTrip = null;
        double bestScore = Double.MAX_VALUE;
        for (TimelineTripEntity trip : candidates) {
            if (trip.getId() == null || matchedTripIds.contains(trip.getId())) {
                continue;
            }
            if (!matchesSource(override, trip)) {
                continue;
            }

            double score = Math.abs(Duration.between(override.getSourceTripTimestamp(), trip.getTimestamp()).getSeconds())
                    + GeoUtils.haversine(override.getSourceStartLatitude(), override.getSourceStartLongitude(),
                    trip.getStartPoint().getY(), trip.getStartPoint().getX())
                    + GeoUtils.haversine(override.getSourceEndLatitude(), override.getSourceEndLongitude(),
                    trip.getEndPoint().getY(), trip.getEndPoint().getX());
            if (score < bestScore) {
                bestScore = score;
                bestTrip = trip;
            }
        }
        return bestTrip;
    }

    private boolean matchesSource(TimelineTripStaySplitOverrideEntity override, TimelineTripEntity trip) {
        long timestampDelta = Math.abs(Duration.between(override.getSourceTripTimestamp(), trip.getTimestamp()).getSeconds());
        if (timestampDelta > maxTimestampDeltaSeconds) {
            return false;
        }
        if (!isWithinRatioBounds(override.getSourceTripDurationSeconds(), trip.getTripDuration(), minDurationRatio, maxDurationRatio)) {
            return false;
        }
        if (!isWithinRatioBounds(override.getSourceDistanceMeters(), trip.getDistanceMeters(), minDistanceRatio, maxDistanceRatio)) {
            return false;
        }

        double startDistance = GeoUtils.haversine(override.getSourceStartLatitude(), override.getSourceStartLongitude(),
                trip.getStartPoint().getY(), trip.getStartPoint().getX());
        double endDistance = GeoUtils.haversine(override.getSourceEndLatitude(), override.getSourceEndLongitude(),
                trip.getEndPoint().getY(), trip.getEndPoint().getX());
        return startDistance <= maxPointDistanceMeters && endDistance <= maxPointDistanceMeters;
    }

    private void syncLocation(TimelineTripStaySplitOverrideEntity override, ResolvedLocation location) {
        override.setStayStartTime(location.stayStartTime());
        override.setStayEndTime(location.stayEndTime());
        override.setAnchorTimestamp(location.anchorTimestamp());
        override.setStayLatitude(location.latitude());
        override.setStayLongitude(location.longitude());
        override.setStayLocationName(normalizeLocationName(location.locationName()));
        override.setStayLocationSource(location.locationSource() != null ? location.locationSource() : LocationSource.HISTORICAL);
        override.setFavoriteLocation(location.favoriteId() != null
                ? entityManager.getReference(FavoritesEntity.class, location.favoriteId())
                : null);
        override.setGeocodingLocation(location.geocodingId() != null
                ? entityManager.getReference(ReverseGeocodingLocationEntity.class, location.geocodingId())
                : null);
    }

    private void validateRequest(TripStaySplitRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Split request is required");
        }
        if (request.getStayStartTime() == null || request.getStayEndTime() == null) {
            throw new IllegalArgumentException("Stay start and end time are required");
        }
        if (request.getAnchorTimestamp() == null) {
            throw new IllegalArgumentException("Selected GPS point timestamp is required");
        }
        if (!request.getStayEndTime().isAfter(request.getStayStartTime())) {
            throw new IllegalArgumentException("Stay end time must be after stay start time");
        }
        if (request.getLatitude() == null || request.getLongitude() == null) {
            throw new IllegalArgumentException("Stay location is required");
        }
        GeoUtils.createPoint(request.getLongitude(), request.getLatitude());
        if (request.getLocationName() != null && request.getLocationName().length() > 500) {
            throw new IllegalArgumentException("Location name must be 500 characters or fewer");
        }
    }

    private void ensureInsideTrip(TimelineTripEntity trip, Instant stayStartTime, Instant stayEndTime) {
        Instant tripStart = trip.getTimestamp();
        Instant tripEnd = tripEnd(trip);
        if (!stayStartTime.isAfter(tripStart) || !stayEndTime.isBefore(tripEnd)) {
            throw new IllegalArgumentException("Stay must be fully inside the trip");
        }
    }

    private void ensureNoTimelineOverlap(UUID userId, Instant stayStartTime, Instant stayEndTime) {
        long stayOverlaps = stayRepository.count("""
                        user.id = ?1
                        and timestamp < ?3
                        and FUNCTION('TIMESTAMPADD', SECOND, stayDuration, timestamp) > ?2
                        """,
                userId, stayStartTime, stayEndTime);
        if (stayOverlaps > 0) {
            throw new IllegalArgumentException("Requested stay overlaps an existing stay");
        }

        List<?> gapOverlaps = dataGapRepository.findByUserIdAndTimeRange(userId, stayStartTime, stayEndTime);
        if (!gapOverlaps.isEmpty()) {
            throw new IllegalArgumentException("Requested stay overlaps an existing data gap");
        }
    }

    private void ensureTimeAnchoredStayLocation(UUID userId,
                                                TimelineTripEntity trip,
                                                ResolvedLocation location,
                                                TimelineConfig config) {
        if (location.anchorTimestamp().isBefore(location.stayStartTime())
                || location.anchorTimestamp().isAfter(location.stayEndTime())) {
            throw new IllegalArgumentException("Selected place is not near GPS points in this time range");
        }
        if (!location.anchorTimestamp().isAfter(trip.getTimestamp())
                || !location.anchorTimestamp().isBefore(tripEnd(trip))) {
            throw new IllegalArgumentException("Selected stay must be an intermediate trip point");
        }

        Double maxAccuracy = TimelineGpsAccuracyFilter.getActiveMaxAccuracyThreshold(config);
        List<GpsPointEntity> points = gpsPointRepository.findEligibleByUserIdAndTimePeriod(
                userId,
                trip.getTimestamp(),
                tripEnd(trip),
                maxAccuracy);
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Original trip has no GPS points");
        }

        double radiusMeters = resolveSplitSelectionRadiusMeters(config);
        int anchorIndex = findNearestTimestampIndex(points, location.anchorTimestamp());
        GpsPointEntity anchorPoint = points.get(anchorIndex);
        long anchorDeltaSeconds = Math.abs(Duration.between(location.anchorTimestamp(), anchorPoint.getTimestamp()).getSeconds());
        double anchorDistanceMeters = distanceMeters(location, anchorPoint);
        if (anchorDeltaSeconds > MANUAL_SPLIT_ANCHOR_MAX_DELTA_SECONDS || anchorDistanceMeters > radiusMeters) {
            throw new IllegalArgumentException("Stay location is not near the selected GPS point");
        }

        int clusterStartIndex = anchorIndex;
        int clusterEndIndex = anchorIndex;
        while (clusterStartIndex > 0 && distanceMeters(location, points.get(clusterStartIndex - 1)) <= radiusMeters) {
            clusterStartIndex--;
        }
        while (clusterEndIndex < points.size() - 1 && distanceMeters(location, points.get(clusterEndIndex + 1)) <= radiusMeters) {
            clusterEndIndex++;
        }
        if (clusterStartIndex == 0 || clusterEndIndex == points.size() - 1) {
            throw new IllegalArgumentException("Selected stay must be an intermediate trip point");
        }

        boolean hasEvidenceInStayWindow = points.stream()
                .anyMatch(point -> !point.getTimestamp().isBefore(location.stayStartTime())
                        && !point.getTimestamp().isAfter(location.stayEndTime())
                        && distanceMeters(location, point) <= radiusMeters);
        if (!hasEvidenceInStayWindow) {
            throw new IllegalArgumentException("Selected place is not near GPS points in this time range");
        }
    }

    private int findNearestTimestampIndex(List<GpsPointEntity> points, Instant timestamp) {
        int nearestIndex = 0;
        long nearestDeltaSeconds = Long.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            long deltaSeconds = Math.abs(Duration.between(timestamp, points.get(i).getTimestamp()).getSeconds());
            if (deltaSeconds < nearestDeltaSeconds) {
                nearestDeltaSeconds = deltaSeconds;
                nearestIndex = i;
            }
        }
        return nearestIndex;
    }

    private double distanceMeters(ResolvedLocation location, GpsPointEntity point) {
        return GeoUtils.haversine(
                location.latitude(),
                location.longitude(),
                point.getLatitude(),
                point.getLongitude());
    }

    private double resolveSplitSelectionRadiusMeters(TimelineConfig config) {
        Integer configuredRadius = config != null ? config.getStaypointRadiusMeters() : null;
        return Math.max(150.0, configuredRadius != null ? configuredRadius.doubleValue() : 0.0);
    }

    private TripStaySplitResponse toResponse(TimelineTripStaySplitOverrideEntity override,
                                             Long originalTripId,
                                             PreparedSplit split,
                                             Instant regenerationStartTime) {
        TimelineStayLocationDTO stayDto = converter.convertStayEntityToDto(split.stay());
        if (override != null && stayDto != null) {
            stayDto.setTripSplitOverrideId(override.getId());
        }

        TimelineTripDTO firstTripDto = converter.convertTripEntityToDto(split.firstTrip());
        TimelineTripDTO secondTripDto = converter.convertTripEntityToDto(split.secondTrip());

        return TripStaySplitResponse.builder()
                .overrideId(override != null ? override.getId() : null)
                .originalTripId(originalTripId)
                .firstTripId(split.firstTrip().getId())
                .stayId(split.stay().getId())
                .secondTripId(split.secondTrip().getId())
                .stayStartTime(split.location().stayStartTime())
                .stayEndTime(split.location().stayEndTime())
                .locationName(split.location().locationName())
                .regenerationStartTime(regenerationStartTime)
                .firstTrip(firstTripDto)
                .stay(stayDto)
                .secondTrip(secondTripDto)
                .build();
    }

    private Instant tripEnd(TimelineTripEntity trip) {
        return trip.getTimestamp().plusSeconds(Math.max(0L, trip.getTripDuration()));
    }

    private boolean isWithinRatioBounds(long sourceValue, long candidateValue, double minRatio, double maxRatio) {
        if (sourceValue <= 0 || candidateValue <= 0) {
            return false;
        }
        double ratio = (double) candidateValue / (double) sourceValue;
        return ratio >= minRatio && ratio <= maxRatio;
    }

    private String normalizeLocationName(String locationName) {
        String normalized = trimToNull(locationName);
        return normalized != null ? normalized : "Unknown location";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record PreparedSplit(
            TimelineTripEntity firstTrip,
            TimelineStayEntity stay,
            TimelineTripEntity secondTrip,
            ResolvedLocation location
    ) {
    }

    private record ResolvedLocation(
            Instant stayStartTime,
            Instant stayEndTime,
            Instant anchorTimestamp,
            double latitude,
            double longitude,
            String locationName,
            Long favoriteId,
            Long geocodingId,
            LocationSource locationSource
    ) {
    }
}
