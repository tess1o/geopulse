package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.service.LocationPointResolver;
import org.github.tess1o.geopulse.shared.service.LocationResolutionResult;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.streaming.model.dto.DataGapStayConversionPreviewDTO;
import org.github.tess1o.geopulse.streaming.model.dto.DataGapStayOverrideRequest;
import org.github.tess1o.geopulse.streaming.model.dto.DataGapStayOverrideResponseDTO;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapStayOverrideEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.shared.DataGapStayOverrideLocationStrategy;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapStayOverrideRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
@Slf4j
public class DataGapStayOverrideService {

    @ConfigProperty(name = "geopulse.timeline.data_gap_stay_override.matching.max_timestamp_delta_seconds",
            defaultValue = "2700")
    long maxTimestampDeltaSeconds;

    @ConfigProperty(name = "geopulse.timeline.data_gap_stay_override.matching.max_boundary_distance_meters",
            defaultValue = "350.0")
    double maxBoundaryDistanceMeters;

    @ConfigProperty(name = "geopulse.timeline.data_gap_stay_override.matching.min_duration_ratio",
            defaultValue = "0.6")
    double minDurationRatio;

    @ConfigProperty(name = "geopulse.timeline.data_gap_stay_override.matching.max_duration_ratio",
            defaultValue = "1.8")
    double maxDurationRatio;

    @ConfigProperty(name = "geopulse.timeline.data_gap_stay_override.adjacent_tolerance_seconds",
            defaultValue = "120")
    long adjacentToleranceSeconds;

    @ConfigProperty(name = "geopulse.timeline.data_gap_stay_override.location_match.max_distance_meters",
            defaultValue = "80.0")
    double sameLocationMaxDistanceMeters;

    private final TimelineDataGapRepository dataGapRepository;
    private final TimelineStayRepository stayRepository;
    private final TimelineDataGapStayOverrideRepository overrideRepository;
    private final GpsPointRepository gpsPointRepository;
    private final LocationPointResolver locationPointResolver;
    private final FavoritesRepository favoritesRepository;
    private final ReverseGeocodingLocationRepository geocodingRepository;
    private final EntityManager entityManager;

    @Inject
    public DataGapStayOverrideService(TimelineDataGapRepository dataGapRepository,
                                      TimelineStayRepository stayRepository,
                                      TimelineDataGapStayOverrideRepository overrideRepository,
                                      GpsPointRepository gpsPointRepository,
                                      LocationPointResolver locationPointResolver,
                                      FavoritesRepository favoritesRepository,
                                      ReverseGeocodingLocationRepository geocodingRepository,
                                      EntityManager entityManager) {
        this.dataGapRepository = dataGapRepository;
        this.stayRepository = stayRepository;
        this.overrideRepository = overrideRepository;
        this.gpsPointRepository = gpsPointRepository;
        this.locationPointResolver = locationPointResolver;
        this.favoritesRepository = favoritesRepository;
        this.geocodingRepository = geocodingRepository;
        this.entityManager = entityManager;
    }

    public Optional<DataGapStayConversionPreviewDTO> previewLatestPointConversion(UUID userId, Long gapId) {
        Optional<TimelineDataGapEntity> gapOptional = dataGapRepository.findByIdAndUserId(gapId, userId);
        if (gapOptional.isEmpty()) {
            return Optional.empty();
        }

        TimelineDataGapEntity gap = gapOptional.get();
        ensureGapCanBeConverted(userId, gap);
        BoundaryPoints boundaries = resolveBoundaryPointsForGap(userId, gap)
                .orElseThrow(() -> new IllegalStateException("Unable to resolve GPS boundary points for data gap"));

        ResolvedLocation location = resolveLatestPointLocation(userId, boundaries.beforePoint());

        return Optional.of(DataGapStayConversionPreviewDTO.builder()
                .dataGapId(gap.getId())
                .startTime(gap.getStartTime())
                .endTime(gap.getEndTime())
                .durationSeconds(gap.getDurationSeconds())
                .anchorLatitude(location.latitude())
                .anchorLongitude(location.longitude())
                .locationName(location.locationName())
                .favoriteId(location.favoriteId())
                .geocodingId(location.geocodingId())
                .build());
    }

    @Transactional
    public Optional<DataGapStayOverrideResponseDTO> convertGapToStay(UUID userId,
                                                                     Long gapId,
                                                                     DataGapStayOverrideRequest request) {
        Optional<TimelineDataGapEntity> gapOptional = dataGapRepository.findByIdAndUserId(gapId, userId);
        if (gapOptional.isEmpty()) {
            return Optional.empty();
        }

        TimelineDataGapEntity gap = gapOptional.get();
        ensureGapCanBeConverted(userId, gap);

        BoundaryPoints boundaries = resolveBoundaryPointsForGap(userId, gap)
                .orElseThrow(() -> new IllegalStateException("Unable to resolve GPS boundary points for data gap"));

        DataGapStayOverrideLocationStrategy strategy = resolveStrategy(request);
        ResolvedLocation resolvedLocation = resolveLocationForRequest(userId, strategy, request, boundaries.beforePoint());

        TimelineDataGapStayOverrideEntity override = upsertOverride(userId, gap, strategy, request, boundaries);
        TimelineStayEntity resultingStay = applyGapConversionAndNormalize(userId, gap, resolvedLocation);

        override.setStay(resultingStay);
        // The matched gap row is removed immediately after conversion.
        // Keep the optional live link null to avoid dangling entity references at flush time.
        override.setDataGap(null);

        dataGapRepository.delete(gap);

        return Optional.of(toResponse(override, gapId, resultingStay, null));
    }

    @Transactional
    public Optional<DataGapStayOverrideResponseDTO> removeManualOverride(UUID userId, Long overrideId) {
        Optional<TimelineDataGapStayOverrideEntity> overrideOptional = overrideRepository.findByIdAndUserId(overrideId, userId);
        if (overrideOptional.isEmpty()) {
            return Optional.empty();
        }

        TimelineDataGapStayOverrideEntity override = overrideOptional.get();
        Instant regenerationStartTime = override.getSourceGapStartTime();

        overrideRepository.delete(override);

        return Optional.of(DataGapStayOverrideResponseDTO.builder()
                .overrideId(overrideId)
                .locationStrategy(override.getLocationStrategy() != null ? override.getLocationStrategy().name() : null)
                .regenerationStartTime(regenerationStartTime)
                .build());
    }

    /**
     * Re-applies all manual Data Gap -> Stay overrides after timeline regeneration.
     *
     * @return number of successfully re-applied overrides
     */
    @Transactional
    public int reapplyManualOverrides(UUID userId) {
        List<TimelineDataGapStayOverrideEntity> overrides = overrideRepository.findByUserId(userId);
        if (overrides.isEmpty()) {
            return 0;
        }

        List<TimelineDataGapEntity> candidateGaps = dataGapRepository.findByUserId(userId);
        if (candidateGaps.isEmpty()) {
            return 0;
        }

        Set<Long> matchedGapIds = new HashSet<>();
        int appliedCount = 0;

        for (TimelineDataGapStayOverrideEntity override : overrides) {
            GapMatch bestMatch = findBestMatch(userId, override, candidateGaps, matchedGapIds);
            if (bestMatch == null) {
                continue;
            }

            ResolvedLocation location;
            try {
                location = resolveLocationForOverride(userId, override, bestMatch.boundaries.beforePoint());
            } catch (Exception ex) {
                log.warn("Skipping override {} for user {} due to location resolution failure: {}",
                        override.getId(), userId, ex.getMessage());
                continue;
            }

            TimelineStayEntity resultingStay = applyGapConversionAndNormalize(userId, bestMatch.gap, location);
            override.setStay(resultingStay);
            // Re-applied overrides also delete the matched gap row in this transaction.
            override.setDataGap(null);

            dataGapRepository.delete(bestMatch.gap);
            if (bestMatch.gap.getId() != null) {
                matchedGapIds.add(bestMatch.gap.getId());
            }
            appliedCount++;
        }

        if (appliedCount > 0) {
            log.info("Re-applied {} manual Data Gap -> Stay overrides for user {}", appliedCount, userId);
        }

        return appliedCount;
    }

    private TimelineDataGapStayOverrideEntity upsertOverride(UUID userId,
                                                             TimelineDataGapEntity gap,
                                                             DataGapStayOverrideLocationStrategy strategy,
                                                             DataGapStayOverrideRequest request,
                                                             BoundaryPoints boundaries) {
        TimelineDataGapStayOverrideEntity override = overrideRepository
                .findByUserIdAndSourceGap(userId, gap.getStartTime(), gap.getEndTime())
                .orElseGet(() -> TimelineDataGapStayOverrideEntity.builder()
                        .user(entityManager.getReference(UserEntity.class, userId))
                        .build());

        override.setLocationStrategy(strategy);

        if (override.getId() == null) {
            // Keep source anchors immutable after creation.
            override.setSourceGapStartTime(gap.getStartTime());
            override.setSourceGapEndTime(gap.getEndTime());
            override.setSourceGapDurationSeconds(gap.getDurationSeconds());
            override.setSourceBeforeLatitude(boundaries.beforePoint().getCoordinates().getY());
            override.setSourceBeforeLongitude(boundaries.beforePoint().getCoordinates().getX());
            override.setSourceAfterLatitude(boundaries.afterPoint().getCoordinates().getY());
            override.setSourceAfterLongitude(boundaries.afterPoint().getCoordinates().getX());
        }

        syncSelectedLocationFields(override, strategy, request);

        if (override.getId() == null) {
            overrideRepository.persist(override);
        }

        return override;
    }

    private void syncSelectedLocationFields(TimelineDataGapStayOverrideEntity override,
                                            DataGapStayOverrideLocationStrategy strategy,
                                            DataGapStayOverrideRequest request) {
        if (strategy != DataGapStayOverrideLocationStrategy.SELECTED_LOCATION || request == null) {
            override.setSelectedFavoriteId(null);
            override.setSelectedGeocodingId(null);
            override.setSelectedLatitude(null);
            override.setSelectedLongitude(null);
            override.setSelectedLocationName(null);
            return;
        }

        override.setSelectedFavoriteId(request.getFavoriteId());
        override.setSelectedGeocodingId(request.getGeocodingId());
        override.setSelectedLatitude(request.getLatitude());
        override.setSelectedLongitude(request.getLongitude());
        override.setSelectedLocationName(trimToNull(request.getLocationName()));
    }

    private DataGapStayOverrideResponseDTO toResponse(TimelineDataGapStayOverrideEntity override,
                                                      Long dataGapId,
                                                      TimelineStayEntity stay,
                                                      Instant regenerationStartTime) {
        Instant start = stay != null ? stay.getTimestamp() : null;
        Instant end = (stay != null && start != null) ? start.plusSeconds(stay.getStayDuration()) : null;

        return DataGapStayOverrideResponseDTO.builder()
                .overrideId(override != null ? override.getId() : null)
                .dataGapId(dataGapId)
                .stayId(stay != null ? stay.getId() : null)
                .locationStrategy(override != null && override.getLocationStrategy() != null
                        ? override.getLocationStrategy().name()
                        : null)
                .locationName(stay != null ? stay.getLocationName() : null)
                .startTime(start)
                .endTime(end)
                .stayDurationSeconds(stay != null ? stay.getStayDuration() : null)
                .regenerationStartTime(regenerationStartTime)
                .build();
    }

    private void ensureGapCanBeConverted(UUID userId, TimelineDataGapEntity gap) {
        if (gap == null || gap.getStartTime() == null || gap.getEndTime() == null) {
            throw new IllegalArgumentException("Invalid data gap record");
        }
        if (!gap.getEndTime().isAfter(gap.getStartTime())) {
            throw new IllegalArgumentException("Data gap has invalid time range");
        }

        Optional<GpsPointEntity> latestGpsPoint = gpsPointRepository.findLatest(userId);
        if (latestGpsPoint.isPresent() && gap.getStartTime().equals(latestGpsPoint.get().getTimestamp())) {
            throw new IllegalStateException("Cannot convert an ongoing data gap");
        }
    }

    private DataGapStayOverrideLocationStrategy resolveStrategy(DataGapStayOverrideRequest request) {
        if (request == null || request.getLocationStrategy() == null || request.getLocationStrategy().isBlank()) {
            return DataGapStayOverrideLocationStrategy.LATEST_POINT;
        }

        try {
            return DataGapStayOverrideLocationStrategy.valueOf(request.getLocationStrategy().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid locationStrategy. Allowed values: LATEST_POINT, SELECTED_LOCATION");
        }
    }

    private ResolvedLocation resolveLocationForRequest(UUID userId,
                                                       DataGapStayOverrideLocationStrategy strategy,
                                                       DataGapStayOverrideRequest request,
                                                       GpsPointEntity beforePoint) {
        if (strategy == DataGapStayOverrideLocationStrategy.SELECTED_LOCATION) {
            return resolveSelectedLocation(userId, request);
        }
        return resolveLatestPointLocation(userId, beforePoint);
    }

    private ResolvedLocation resolveLocationForOverride(UUID userId,
                                                        TimelineDataGapStayOverrideEntity override,
                                                        GpsPointEntity candidateBeforePoint) {
        if (override.getLocationStrategy() == DataGapStayOverrideLocationStrategy.SELECTED_LOCATION) {
            DataGapStayOverrideRequest request = new DataGapStayOverrideRequest();
            request.setFavoriteId(override.getSelectedFavoriteId());
            request.setGeocodingId(override.getSelectedGeocodingId());
            request.setLatitude(override.getSelectedLatitude());
            request.setLongitude(override.getSelectedLongitude());
            request.setLocationName(override.getSelectedLocationName());
            return resolveSelectedLocation(userId, request);
        }

        return resolveLatestPointLocation(userId, candidateBeforePoint);
    }

    private ResolvedLocation resolveSelectedLocation(UUID userId, DataGapStayOverrideRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("selected location payload is required");
        }

        boolean hasFavorite = request.getFavoriteId() != null;
        boolean hasGeocoding = request.getGeocodingId() != null;
        boolean hasCoordinates = request.getLatitude() != null || request.getLongitude() != null;

        int selectedSources = (hasFavorite ? 1 : 0) + (hasGeocoding ? 1 : 0) + (hasCoordinates ? 1 : 0);
        if (selectedSources == 0) {
            throw new IllegalArgumentException("SELECTED_LOCATION requires favoriteId, geocodingId, or latitude/longitude");
        }
        if (selectedSources > 1) {
            throw new IllegalArgumentException("SELECTED_LOCATION accepts only one location source");
        }

        if (hasFavorite) {
            FavoritesEntity favorite = favoritesRepository.findByIdAndUserId(request.getFavoriteId(), userId)
                    .orElseThrow(() -> new IllegalArgumentException("Selected favorite not found"));
            Point favoritePoint = extractGeometryCenter(favorite.getGeometry());
            return new ResolvedLocation(
                    favoritePoint.getY(),
                    favoritePoint.getX(),
                    favorite.getName(),
                    favorite.getId(),
                    null,
                    LocationSource.FAVORITE
            );
        }

        if (hasGeocoding) {
            ReverseGeocodingLocationEntity geocoding = geocodingRepository.findByIdOptional(request.getGeocodingId())
                    .orElseThrow(() -> new IllegalArgumentException("Selected geocoding location not found"));
            if (geocoding.getUser() != null && !userId.equals(geocoding.getUser().getId())) {
                throw new IllegalArgumentException("Selected geocoding location is not accessible");
            }

            Point geocodingPoint = geocoding.getResultCoordinates() != null
                    ? geocoding.getResultCoordinates()
                    : geocoding.getRequestCoordinates();

            return new ResolvedLocation(
                    geocodingPoint.getY(),
                    geocodingPoint.getX(),
                    geocoding.getDisplayName(),
                    null,
                    geocoding.getId(),
                    LocationSource.GEOCODING
            );
        }

        if (request.getLatitude() == null || request.getLongitude() == null) {
            throw new IllegalArgumentException("Both latitude and longitude are required for custom location");
        }

        Point customPoint = GeoUtils.createPoint(request.getLongitude(), request.getLatitude());
        LocationResolutionResult locationResult = locationPointResolver.resolveLocationWithReferences(userId, customPoint);

        double latitude = customPoint.getY();
        double longitude = customPoint.getX();
        if (locationResult.hasAnchoredCoordinates()) {
            latitude = locationResult.getAnchorLatitude();
            longitude = locationResult.getAnchorLongitude();
        }

        String locationName = trimToNull(request.getLocationName());
        if (locationName == null) {
            locationName = normalizeLocationName(locationResult.getLocationName());
        }

        LocationSource source = locationResult.getFavoriteId() != null
                ? LocationSource.FAVORITE
                : locationResult.getGeocodingId() != null
                ? LocationSource.GEOCODING
                : LocationSource.HISTORICAL;

        return new ResolvedLocation(
                latitude,
                longitude,
                locationName,
                locationResult.getFavoriteId(),
                locationResult.getGeocodingId(),
                source
        );
    }

    private ResolvedLocation resolveLatestPointLocation(UUID userId, GpsPointEntity beforePoint) {
        Point beforeCoordinates = beforePoint.getCoordinates();
        LocationResolutionResult locationResult = locationPointResolver.resolveLocationWithReferences(userId, beforeCoordinates);

        double latitude = beforeCoordinates.getY();
        double longitude = beforeCoordinates.getX();
        if (locationResult.hasAnchoredCoordinates()) {
            latitude = locationResult.getAnchorLatitude();
            longitude = locationResult.getAnchorLongitude();
        }

        LocationSource source = locationResult.getFavoriteId() != null
                ? LocationSource.FAVORITE
                : locationResult.getGeocodingId() != null
                ? LocationSource.GEOCODING
                : LocationSource.HISTORICAL;

        return new ResolvedLocation(
                latitude,
                longitude,
                normalizeLocationName(locationResult.getLocationName()),
                locationResult.getFavoriteId(),
                locationResult.getGeocodingId(),
                source
        );
    }

    private Optional<BoundaryPoints> resolveBoundaryPointsForGap(UUID userId, TimelineDataGapEntity gap) {
        Optional<GpsPointEntity> beforePoint = gpsPointRepository.findLatestByUserIdAtOrBeforeTimestamp(userId, gap.getStartTime());
        Optional<GpsPointEntity> afterPoint = gpsPointRepository.findEarliestByUserIdAtOrAfterTimestamp(userId, gap.getEndTime());

        if (beforePoint.isEmpty() || afterPoint.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new BoundaryPoints(beforePoint.get(), afterPoint.get()));
    }

    private TimelineStayEntity applyGapConversionAndNormalize(UUID userId,
                                                              TimelineDataGapEntity gap,
                                                              ResolvedLocation location) {
        Instant gapStart = gap.getStartTime();
        Instant gapEnd = gap.getEndTime();

        TimelineStayEntity previousStay = stayRepository.findLatestByUserIdAtOrBeforeTimestamp(userId, gapStart)
                .orElse(null);
        TimelineStayEntity nextStay = stayRepository.findEarliestByUserIdAtOrAfterTimestamp(userId, gapEnd)
                .orElse(null);

        boolean previousAdjacent = isAdjacentPrevious(previousStay, gapStart);
        boolean nextAdjacent = isAdjacentNext(nextStay, gapEnd);

        boolean previousSameLocation = previousAdjacent && isSameLocation(previousStay, location);
        boolean nextSameLocation = nextAdjacent && isSameLocation(nextStay, location);

        if (previousSameLocation && nextSameLocation && isSameLocation(previousStay, nextStay)) {
            Instant previousStart = previousStay.getTimestamp();
            Instant nextEnd = stayEnd(nextStay);
            previousStay.setStayDuration(Duration.between(previousStart, nextEnd).getSeconds());
            stayRepository.delete(nextStay);
            return previousStay;
        }

        if (previousSameLocation) {
            Instant previousStart = previousStay.getTimestamp();
            Instant extendedEnd = gapEnd.isAfter(stayEnd(previousStay)) ? gapEnd : stayEnd(previousStay);
            previousStay.setStayDuration(Duration.between(previousStart, extendedEnd).getSeconds());
            return previousStay;
        }

        if (nextSameLocation) {
            Instant nextEnd = stayEnd(nextStay);
            nextStay.setTimestamp(gapStart);
            nextStay.setStayDuration(Duration.between(gapStart, nextEnd).getSeconds());
            return nextStay;
        }

        TimelineStayEntity newStay = TimelineStayEntity.builder()
                .user(entityManager.getReference(UserEntity.class, userId))
                .timestamp(gapStart)
                .stayDuration(gap.getDurationSeconds())
                .location(GeoUtils.createPoint(location.longitude(), location.latitude()))
                .locationName(location.locationName())
                .locationSource(location.locationSource())
                .build();

        if (location.favoriteId() != null) {
            FavoritesEntity favoriteRef = entityManager.getReference(FavoritesEntity.class, location.favoriteId());
            newStay.setFavoriteLocation(favoriteRef);
        }
        if (location.geocodingId() != null) {
            ReverseGeocodingLocationEntity geocodingRef = entityManager.getReference(ReverseGeocodingLocationEntity.class, location.geocodingId());
            newStay.setGeocodingLocation(geocodingRef);
        }

        stayRepository.persist(newStay);
        return newStay;
    }

    private GapMatch findBestMatch(UUID userId,
                                   TimelineDataGapStayOverrideEntity override,
                                   List<TimelineDataGapEntity> candidateGaps,
                                   Set<Long> matchedGapIds) {
        GapMatch bestMatch = null;

        for (TimelineDataGapEntity candidate : candidateGaps) {
            if (candidate.getId() == null || matchedGapIds.contains(candidate.getId())) {
                continue;
            }

            long startDelta = Math.abs(Duration.between(override.getSourceGapStartTime(), candidate.getStartTime()).getSeconds());
            long endDelta = Math.abs(Duration.between(override.getSourceGapEndTime(), candidate.getEndTime()).getSeconds());
            if (startDelta > maxTimestampDeltaSeconds || endDelta > maxTimestampDeltaSeconds) {
                continue;
            }

            if (!isWithinRatioBounds(override.getSourceGapDurationSeconds(), candidate.getDurationSeconds(),
                    minDurationRatio, maxDurationRatio)) {
                continue;
            }

            Optional<BoundaryPoints> boundariesOptional = resolveBoundaryPointsForGap(userId, candidate);
            if (boundariesOptional.isEmpty()) {
                continue;
            }
            BoundaryPoints boundaries = boundariesOptional.get();

            double beforeDistance = GeoUtils.haversine(
                    override.getSourceBeforeLatitude(),
                    override.getSourceBeforeLongitude(),
                    boundaries.beforePoint().getCoordinates().getY(),
                    boundaries.beforePoint().getCoordinates().getX()
            );
            double afterDistance = GeoUtils.haversine(
                    override.getSourceAfterLatitude(),
                    override.getSourceAfterLongitude(),
                    boundaries.afterPoint().getCoordinates().getY(),
                    boundaries.afterPoint().getCoordinates().getX()
            );

            if (beforeDistance > maxBoundaryDistanceMeters || afterDistance > maxBoundaryDistanceMeters) {
                continue;
            }

            long durationDelta = Math.abs(override.getSourceGapDurationSeconds() - candidate.getDurationSeconds());
            double score = startDelta + endDelta + beforeDistance + afterDistance + (durationDelta * 0.5);

            if (bestMatch == null || score < bestMatch.score) {
                bestMatch = new GapMatch(candidate, boundaries, score);
            }
        }

        return bestMatch;
    }

    private boolean isWithinRatioBounds(long sourceValue, long candidateValue, double minRatio, double maxRatio) {
        if (sourceValue <= 0 || candidateValue <= 0) {
            return false;
        }
        double ratio = (double) candidateValue / (double) sourceValue;
        return ratio >= minRatio && ratio <= maxRatio;
    }

    private boolean isAdjacentPrevious(TimelineStayEntity stay, Instant gapStart) {
        if (stay == null || stay.getTimestamp() == null) {
            return false;
        }
        Instant stayEnd = stayEnd(stay);
        return Math.abs(Duration.between(stayEnd, gapStart).getSeconds()) <= adjacentToleranceSeconds;
    }

    private boolean isAdjacentNext(TimelineStayEntity stay, Instant gapEnd) {
        if (stay == null || stay.getTimestamp() == null) {
            return false;
        }
        return Math.abs(Duration.between(stay.getTimestamp(), gapEnd).getSeconds()) <= adjacentToleranceSeconds;
    }

    private Instant stayEnd(TimelineStayEntity stay) {
        return stay.getTimestamp().plusSeconds(Math.max(0L, stay.getStayDuration()));
    }

    private boolean isSameLocation(TimelineStayEntity stay, ResolvedLocation location) {
        if (stay == null || stay.getLocation() == null || location == null) {
            return false;
        }

        Long stayFavoriteId = stay.getFavoriteLocation() != null ? stay.getFavoriteLocation().getId() : null;
        Long stayGeocodingId = stay.getGeocodingLocation() != null ? stay.getGeocodingLocation().getId() : null;

        if (stayFavoriteId != null && location.favoriteId() != null) {
            return stayFavoriteId.equals(location.favoriteId());
        }

        if (stayGeocodingId != null && location.geocodingId() != null) {
            return stayGeocodingId.equals(location.geocodingId());
        }

        double distanceMeters = GeoUtils.haversine(
                stay.getLocation().getY(),
                stay.getLocation().getX(),
                location.latitude(),
                location.longitude()
        );

        return distanceMeters <= sameLocationMaxDistanceMeters;
    }

    private boolean isSameLocation(TimelineStayEntity first, TimelineStayEntity second) {
        if (first == null || second == null || first.getLocation() == null || second.getLocation() == null) {
            return false;
        }

        Long firstFavoriteId = first.getFavoriteLocation() != null ? first.getFavoriteLocation().getId() : null;
        Long secondFavoriteId = second.getFavoriteLocation() != null ? second.getFavoriteLocation().getId() : null;
        if (firstFavoriteId != null && secondFavoriteId != null) {
            return firstFavoriteId.equals(secondFavoriteId);
        }

        Long firstGeocodingId = first.getGeocodingLocation() != null ? first.getGeocodingLocation().getId() : null;
        Long secondGeocodingId = second.getGeocodingLocation() != null ? second.getGeocodingLocation().getId() : null;
        if (firstGeocodingId != null && secondGeocodingId != null) {
            return firstGeocodingId.equals(secondGeocodingId);
        }

        double distanceMeters = GeoUtils.haversine(
                first.getLocation().getY(),
                first.getLocation().getX(),
                second.getLocation().getY(),
                second.getLocation().getX()
        );

        return distanceMeters <= sameLocationMaxDistanceMeters;
    }

    private Point extractGeometryCenter(Geometry geometry) {
        if (geometry == null) {
            throw new IllegalArgumentException("Selected favorite has no geometry");
        }
        if (geometry instanceof Point point) {
            return point;
        }
        return geometry.getCentroid();
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

    private record ResolvedLocation(
            double latitude,
            double longitude,
            String locationName,
            Long favoriteId,
            Long geocodingId,
            LocationSource locationSource
    ) {
    }

    private record BoundaryPoints(GpsPointEntity beforePoint, GpsPointEntity afterPoint) {
    }

    private static class GapMatch {
        private final TimelineDataGapEntity gap;
        private final BoundaryPoints boundaries;
        private final double score;

        private GapMatch(TimelineDataGapEntity gap, BoundaryPoints boundaries, double score) {
            this.gap = gap;
            this.boundaries = boundaries;
            this.score = score;
        }
    }
}
