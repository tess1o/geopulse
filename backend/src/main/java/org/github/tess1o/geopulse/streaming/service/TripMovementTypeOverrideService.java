package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * Regeneration can start a trip hundreds of metres earlier (see the issue #631 replay) but not kilometres away;
     * another trip elsewhere in the same time window is not the corrected one.
     */
    private static final double MAX_ANCHOR_DISTANCE_METERS = 2_000.0;

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
     * Removes one correction. The trip it is attached to, if any, gets its detected movement type back.
     *
     * @return false when the user has no such correction
     */
    @Transactional
    public boolean removeOverride(UUID userId, Long overrideId) {
        Optional<TimelineTripMovementOverrideEntity> override = overrideRepository.find("id = ?1 and user.id = ?2",
                overrideId, userId).firstResultOptional();
        if (override.isEmpty()) {
            return false;
        }
        TimelineTripEntity trip = override.get().getTrip();
        if (trip != null) {
            trip.setMovementType(classifyAutomatically(userId, trip));
            trip.setMovementTypeSource(MovementTypeSource.AUTO);
        }
        overrideRepository.delete(override.get());
        return true;
    }

    /**
     * Removes the corrections made on trips that lay mostly within {@code from}..{@code to}, such as the legs of a
     * split that is undone: those trips no longer exist once the split is gone.
     *
     * @return number of corrections removed
     */
    @Transactional
    public int removeOverridesWithin(UUID userId, Instant from, Instant to) {
        int removed = 0;
        for (TimelineTripMovementOverrideEntity override : overrideRepository.findByUserId(userId)) {
            Instant start = override.getSourceTripTimestamp();
            long shared = sharedSeconds(start, start.plusSeconds(override.getSourceTripDurationSeconds()), from, to);
            if (isMostOf(shared, override.getSourceTripDurationSeconds())) {
                overrideRepository.delete(override);
                removed++;
            }
        }
        return removed;
    }

    /**
     * Records a movement type for the trip travelled during {@code source}, not attached to any trip yet: the next
     * regeneration attaches it to the trip it produces there, as it does for corrections whose trip was rebuilt.
     */
    @Transactional
    public void addPendingOverride(UUID userId, SourceTrip source, TripType movementType) {
        List<TimelineTripMovementOverrideEntity> existing = new ArrayList<>(
                overrideRepository.findByUserIdAndSource(userId, source.start(), source.durationSeconds()));
        // A repeated reconstruction or undo must update the one correction for this travelled interval, not add
        // another one that can never attach. Older duplicates are folded into the row we keep.
        existing.sort(Comparator.comparing(TimelineTripMovementOverrideEntity::getId));
        TimelineTripMovementOverrideEntity override = existing.isEmpty()
                ? TimelineTripMovementOverrideEntity.builder()
                        .user(entityManager.getReference(UserEntity.class, userId))
                        .build()
                : existing.removeLast();
        for (TimelineTripMovementOverrideEntity duplicate : existing) {
            overrideRepository.delete(duplicate);
        }
        override.setMovementType(movementType.name());
        override.setSourceTripTimestamp(source.start());
        override.setSourceTripDurationSeconds(source.durationSeconds());
        override.setSourceDistanceMeters(source.distanceMeters());
        override.setSourceStartLatitude(source.startLatitude());
        override.setSourceStartLongitude(source.startLongitude());
        override.setSourceEndLatitude(source.endLatitude());
        override.setSourceEndLongitude(source.endLongitude());
        if (override.getId() == null) {
            overrideRepository.persist(override);
        }
    }

    /** Where and when a trip was travelled, as recorded with a correction. */
    public record SourceTrip(Instant start, long durationSeconds, long distanceMeters,
                             double startLatitude, double startLongitude, double endLatitude, double endLongitude) {
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

        Set<Long> matchedTripIds = new HashSet<>();
        int appliedCount = 0;

        for (TimelineTripMovementOverrideEntity override : unmatchedOverrides) {
            // Only a trip sharing time with the corrected one can match. Reading just those keeps a correction whose
            // trip is gone for good from making every regeneration read all of the user's trips.
            Instant sourceStart = override.getSourceTripTimestamp();
            List<TimelineTripEntity> candidateTrips = tripRepository
                    .findOverlapping(userId, sourceStart, sourceStart.plusSeconds(override.getSourceTripDurationSeconds()))
                    .stream()
                    .filter(trip -> trip.getMovementTypeSource() == null || trip.getMovementTypeSource() == MovementTypeSource.AUTO)
                    .toList();
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

            long sharedSeconds = sharedSeconds(sourceStart, sourceEnd, trip.getTimestamp(),
                    trip.getTimestamp().plusSeconds(trip.getTripDuration()));

            if (!isMostOf(sharedSeconds, override.getSourceTripDurationSeconds())
                    || !isMostOf(sharedSeconds, trip.getTripDuration())) {
                continue;
            }
            // The time can be the same for a different trip: the corrected trip's points were replaced by another
            // drive in that window. Its ends stay where they were unless the trip is the same one.
            if (!endsNear(override, trip)) {
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

    private static boolean endsNear(TimelineTripMovementOverrideEntity override, TimelineTripEntity trip) {
        if (trip.getStartPoint() == null || trip.getEndPoint() == null) {
            return false;
        }
        double startDistance = GeoUtils.haversine(override.getSourceStartLatitude(),
                override.getSourceStartLongitude(), trip.getStartPoint().getY(), trip.getStartPoint().getX());
        double endDistance = GeoUtils.haversine(override.getSourceEndLatitude(),
                override.getSourceEndLongitude(), trip.getEndPoint().getY(), trip.getEndPoint().getX());
        return startDistance <= MAX_ANCHOR_DISTANCE_METERS && endDistance <= MAX_ANCHOR_DISTANCE_METERS;
    }

    private static long sharedSeconds(Instant firstStart, Instant firstEnd, Instant secondStart, Instant secondEnd) {
        Instant sharedStart = firstStart.isAfter(secondStart) ? firstStart : secondStart;
        Instant sharedEnd = firstEnd.isBefore(secondEnd) ? firstEnd : secondEnd;
        return Duration.between(sharedStart, sharedEnd).getSeconds();
    }

    private static boolean isMostOf(long sharedSeconds, long durationSeconds) {
        return durationSeconds > 0 && sharedSeconds * 2 > durationSeconds;
    }
}
