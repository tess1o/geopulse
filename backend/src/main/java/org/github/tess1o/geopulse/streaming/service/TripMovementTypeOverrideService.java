package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
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
import java.time.Instant;
import java.util.*;

@ApplicationScoped
@Slf4j
public class TripMovementTypeOverrideService {

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

    /**
     * The regenerated trip that shares most of its time with the corrected trip, and most of the corrected trip's
     * time with it. Regeneration moves trip boundaries (a departure detected a few points earlier, a stop found or
     * dropped), so start and end points and distance can change by hundreds of meters, but the time the user
     * travelled stays the same. Requiring more than half of the corrected trip means at most one trip that does not
     * overlap others can qualify; requiring more than half of the candidate keeps a correction off a much longer trip
     * it became only a part of.
     */
    private TimelineTripEntity findBestMatch(TimelineTripMovementOverrideEntity override,
                                             List<TimelineTripEntity> candidateTrips,
                                             Set<Long> matchedTripIds) {
        Instant sourceStart = override.getSourceTripTimestamp();
        Instant sourceEnd = sourceStart.plusSeconds(override.getSourceTripDurationSeconds());
        TimelineTripEntity bestTrip = null;
        long bestSharedSeconds = 0;

        for (TimelineTripEntity trip : candidateTrips) {
            if (trip.getId() == null || matchedTripIds.contains(trip.getId()) || trip.getTimestamp() == null) {
                continue;
            }

            Instant tripEnd = trip.getTimestamp().plusSeconds(trip.getTripDuration());
            Instant sharedStart = sourceStart.isAfter(trip.getTimestamp()) ? sourceStart : trip.getTimestamp();
            Instant sharedEnd = sourceEnd.isBefore(tripEnd) ? sourceEnd : tripEnd;
            long sharedSeconds = Duration.between(sharedStart, sharedEnd).getSeconds();

            if (!isMostOf(sharedSeconds, override.getSourceTripDurationSeconds())
                    || !isMostOf(sharedSeconds, trip.getTripDuration())) {
                continue;
            }
            if (sharedSeconds > bestSharedSeconds) {
                bestSharedSeconds = sharedSeconds;
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

    private static boolean isMostOf(long sharedSeconds, long durationSeconds) {
        return durationSeconds > 0 && sharedSeconds * 2 > durationSeconds;
    }
}
