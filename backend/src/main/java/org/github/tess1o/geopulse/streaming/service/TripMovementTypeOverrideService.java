package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.model.dto.TripMovementTypeUpdateResponseDTO;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripMovementOverrideEntity;
import org.github.tess1o.geopulse.streaming.model.shared.MovementTypeSource;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripMovementOverrideRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.service.trips.TravelClassification;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Duration;
import java.util.*;

@ApplicationScoped
@Slf4j
public class TripMovementTypeOverrideService {

    @ConfigProperty(name = "geopulse.timeline.trip.movement_override.matching.max_timestamp_delta_seconds",
            defaultValue = "2700")
    long maxTimestampDeltaSeconds;

    @ConfigProperty(name = "geopulse.timeline.trip.movement_override.matching.max_point_distance_meters",
            defaultValue = "350.0")
    double maxPointDistanceMeters;

    @ConfigProperty(name = "geopulse.timeline.trip.movement_override.matching.min_duration_ratio",
            defaultValue = "0.6")
    double minDurationRatio;

    @ConfigProperty(name = "geopulse.timeline.trip.movement_override.matching.max_duration_ratio",
            defaultValue = "1.8")
    double maxDurationRatio;

    @ConfigProperty(name = "geopulse.timeline.trip.movement_override.matching.min_distance_ratio",
            defaultValue = "0.6")
    double minDistanceRatio;

    @ConfigProperty(name = "geopulse.timeline.trip.movement_override.matching.max_distance_ratio",
            defaultValue = "1.8")
    double maxDistanceRatio;

    private final TimelineTripRepository tripRepository;
    private final TimelineTripMovementOverrideRepository overrideRepository;
    private final TimelineConfigurationProvider configProvider;
    private final TravelClassification travelClassification;
    private final EntityManager entityManager;

    @Inject
    public TripMovementTypeOverrideService(TimelineTripRepository tripRepository,
                                           TimelineTripMovementOverrideRepository overrideRepository,
                                           TimelineConfigurationProvider configProvider,
                                           TravelClassification travelClassification,
                                           EntityManager entityManager) {
        this.tripRepository = tripRepository;
        this.overrideRepository = overrideRepository;
        this.configProvider = configProvider;
        this.travelClassification = travelClassification;
        this.entityManager = entityManager;
    }

    @Transactional
    public Optional<TripMovementTypeUpdateResponseDTO> setManualMovementType(UUID userId, Long tripId, TripType movementType) {
        Optional<TimelineTripEntity> tripOptional = findTripOwnedByUser(userId, tripId);
        if (tripOptional.isEmpty()) {
            return Optional.empty();
        }

        TimelineTripEntity trip = tripOptional.get();
        trip.setMovementType(movementType.name());
        trip.setMovementTypeSource(MovementTypeSource.MANUAL);

        upsertOverride(userId, trip, movementType.name());

        String algorithmClassification = classifyAutomatically(userId, trip);
        return Optional.of(new TripMovementTypeUpdateResponseDTO(
                trip.getId(),
                trip.getMovementType(),
                MovementTypeSource.MANUAL.name(),
                algorithmClassification
        ));
    }

    @Transactional
    public Optional<TripMovementTypeUpdateResponseDTO> resetToAutomaticMovementType(UUID userId, Long tripId) {
        Optional<TimelineTripEntity> tripOptional = findTripOwnedByUser(userId, tripId);
        if (tripOptional.isEmpty()) {
            return Optional.empty();
        }

        TimelineTripEntity trip = tripOptional.get();
        String algorithmClassification = classifyAutomatically(userId, trip);

        trip.setMovementType(algorithmClassification);
        trip.setMovementTypeSource(MovementTypeSource.AUTO);

        overrideRepository.deleteByUserIdAndTripId(userId, tripId);

        return Optional.of(new TripMovementTypeUpdateResponseDTO(
                trip.getId(),
                trip.getMovementType(),
                MovementTypeSource.AUTO.name(),
                algorithmClassification
        ));
    }

    /**
     * Re-attach and re-apply manual overrides after timeline trips were regenerated.
     *
     * @return number of overrides successfully applied to regenerated trips
     */
    @Transactional
    public int reapplyManualOverrides(UUID userId) {
        List<TimelineTripMovementOverrideEntity> unmatchedOverrides = overrideRepository.findUnmatchedByUserId(userId);
        if (unmatchedOverrides.isEmpty()) {
            return 0;
        }

        List<TimelineTripEntity> candidateTrips = tripRepository.findByUser(userId).stream()
                .filter(trip -> trip.getMovementTypeSource() == null || trip.getMovementTypeSource() == MovementTypeSource.AUTO)
                .toList();

        if (candidateTrips.isEmpty()) {
            return 0;
        }

        Set<Long> matchedTripIds = new HashSet<>();
        int appliedCount = 0;

        for (TimelineTripMovementOverrideEntity override : unmatchedOverrides) {
            TimelineTripEntity bestMatch = findBestMatch(override, candidateTrips, matchedTripIds);
            if (bestMatch == null) {
                continue;
            }

            bestMatch.setMovementType(override.getMovementType());
            bestMatch.setMovementTypeSource(MovementTypeSource.MANUAL);

            // Keep source_* anchor fields immutable. They represent the user-selected original trip
            // and must not drift to a different merged/split trip over time.
            override.setTrip(bestMatch);

            matchedTripIds.add(bestMatch.getId());
            appliedCount++;
        }

        if (appliedCount > 0) {
            log.info("Re-applied {} manual trip movement overrides for user {}", appliedCount, userId);
        }

        return appliedCount;
    }

    private TimelineTripEntity findBestMatch(TimelineTripMovementOverrideEntity override,
                                             List<TimelineTripEntity> candidateTrips,
                                             Set<Long> matchedTripIds) {
        TimelineTripEntity bestTrip = null;
        double bestScore = Double.MAX_VALUE;

        for (TimelineTripEntity trip : candidateTrips) {
            if (trip.getId() == null || matchedTripIds.contains(trip.getId())) {
                continue;
            }
            if (trip.getStartPoint() == null || trip.getEndPoint() == null || trip.getTimestamp() == null) {
                continue;
            }

            long timestampDeltaSeconds = Math.abs(Duration.between(
                    override.getSourceTripTimestamp(),
                    trip.getTimestamp()
            ).getSeconds());

            if (timestampDeltaSeconds > maxTimestampDeltaSeconds) {
                continue;
            }

            double startDistance = GeoUtils.haversine(
                    override.getSourceStartLatitude(),
                    override.getSourceStartLongitude(),
                    trip.getStartPoint().getY(),
                    trip.getStartPoint().getX()
            );
            double endDistance = GeoUtils.haversine(
                    override.getSourceEndLatitude(),
                    override.getSourceEndLongitude(),
                    trip.getEndPoint().getY(),
                    trip.getEndPoint().getX()
            );

            if (startDistance > maxPointDistanceMeters || endDistance > maxPointDistanceMeters) {
                continue;
            }

            long durationDelta = Math.abs(override.getSourceTripDurationSeconds() - trip.getTripDuration());
            long distanceDelta = Math.abs(override.getSourceDistanceMeters() - trip.getDistanceMeters());
            if (!isWithinRatioBounds(override.getSourceTripDurationSeconds(), trip.getTripDuration(),
                    minDurationRatio, maxDurationRatio)) {
                continue;
            }
            if (!isWithinRatioBounds(override.getSourceDistanceMeters(), trip.getDistanceMeters(),
                    minDistanceRatio, maxDistanceRatio)) {
                continue;
            }

            // Weighted score: timestamp + geometry proximity + shape similarity.
            double score = timestampDeltaSeconds
                    + (startDistance + endDistance)
                    + (durationDelta * 0.6)
                    + (distanceDelta * 0.04);

            if (score < bestScore) {
                bestScore = score;
                bestTrip = trip;
            }
        }

        return bestTrip;
    }

    private Optional<TimelineTripEntity> findTripOwnedByUser(UUID userId, Long tripId) {
        return tripRepository.findByIdOptional(tripId)
                .filter(trip -> trip.getUser() != null && userId.equals(trip.getUser().getId()));
    }

    private void upsertOverride(UUID userId, TimelineTripEntity trip, String movementType) {
        TimelineTripMovementOverrideEntity override = overrideRepository.findByUserIdAndTripId(userId, trip.getId())
                .orElseGet(() -> TimelineTripMovementOverrideEntity.builder()
                        .user(entityManager.getReference(UserEntity.class, userId))
                        .build());

        override.setTrip(trip);
        override.setMovementType(movementType);
        syncAnchorFields(override, trip);

        if (override.getId() == null) {
            overrideRepository.persist(override);
        }
    }

    private void syncAnchorFields(TimelineTripMovementOverrideEntity override, TimelineTripEntity trip) {
        override.setSourceTripTimestamp(trip.getTimestamp());
        override.setSourceTripDurationSeconds(trip.getTripDuration());
        override.setSourceDistanceMeters(trip.getDistanceMeters());
        override.setSourceStartLatitude(trip.getStartPoint().getY());
        override.setSourceStartLongitude(trip.getStartPoint().getX());
        override.setSourceEndLatitude(trip.getEndPoint().getY());
        override.setSourceEndLongitude(trip.getEndPoint().getX());
    }

    private String classifyAutomatically(UUID userId, TimelineTripEntity trip) {
        TimelineConfig config = configProvider.getConfigurationForUser(userId);
        return travelClassification.classifyTravelType(trip, config).name();
    }

    private boolean isWithinRatioBounds(long sourceValue, long candidateValue, double minRatio, double maxRatio) {
        if (sourceValue <= 0 || candidateValue <= 0) {
            return false;
        }
        double ratio = (double) candidateValue / (double) sourceValue;
        return ratio >= minRatio && ratio <= maxRatio;
    }
}
