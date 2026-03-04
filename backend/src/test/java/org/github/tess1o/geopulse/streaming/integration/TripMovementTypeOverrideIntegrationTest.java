package org.github.tess1o.geopulse.streaming.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.CleanupHelper;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.shared.MovementTypeSource;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripMovementOverrideRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.TripClassificationDetailsService;
import org.github.tess1o.geopulse.streaming.service.TripMovementTypeOverrideService;
import org.github.tess1o.geopulse.streaming.service.trips.TripReclassificationService;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.user.model.TimelinePreferences;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
class TripMovementTypeOverrideIntegrationTest {

    private static final double HOME_LAT = 40.7589;
    private static final double HOME_LON = -73.9851;
    private static final double OFFICE_LAT = 40.7505;
    private static final double OFFICE_LON = -73.9934;

    @Inject
    StreamingTimelineGenerationService timelineGenerationService;

    @Inject
    TripMovementTypeOverrideService movementTypeOverrideService;

    @Inject
    TripReclassificationService tripReclassificationService;

    @Inject
    TripClassificationDetailsService tripClassificationDetailsService;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    TimelineTripMovementOverrideRepository overrideRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    CleanupHelper cleanupHelper;

    @Inject
    EntityManager entityManager;

    private final GeometryFactory geometryFactory = new GeometryFactory();

    private UserEntity testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        cleanupHelper.cleanupAll();

        testUser = new UserEntity();
        testUser.setEmail("trip-override-test@geopulse.app");
        testUser.setFullName("Trip Override User");
        testUser.setPasswordHash("test");
        userRepository.persist(testUser);

        createGpsScenarioWithTwoTrips(testUser);
        timelineGenerationService.regenerateFullTimeline(testUser.getId());
    }

    @Test
    @Transactional
    void shouldSetManualMovementTypeAndPersistOverride() {
        TimelineTripEntity trip = getSortedTrips().get(0);

        var result = movementTypeOverrideService.setManualMovementType(testUser.getId(), trip.getId(), TripType.FLIGHT);

        assertThat(result).isPresent();
        assertThat(result.get().movementType()).isEqualTo("FLIGHT");
        assertThat(result.get().movementTypeSource()).isEqualTo("MANUAL");

        TimelineTripEntity updatedTrip = tripRepository.findById(trip.getId());
        assertThat(updatedTrip.getMovementType()).isEqualTo("FLIGHT");
        assertThat(updatedTrip.getMovementTypeSource()).isEqualTo(MovementTypeSource.MANUAL);

        var override = overrideRepository.findByUserIdAndTripId(testUser.getId(), trip.getId());
        assertThat(override).isPresent();
        assertThat(override.get().getMovementType()).isEqualTo("FLIGHT");
        assertThat(override.get().getTrip()).isNotNull();
    }

    @Test
    @Transactional
    void shouldResetManualMovementTypeBackToAuto() {
        TimelineTripEntity trip = getSortedTrips().get(0);
        movementTypeOverrideService.setManualMovementType(testUser.getId(), trip.getId(), TripType.FLIGHT);

        var result = movementTypeOverrideService.resetToAutomaticMovementType(testUser.getId(), trip.getId());

        assertThat(result).isPresent();
        assertThat(result.get().movementTypeSource()).isEqualTo("AUTO");
        assertThat(result.get().movementType()).isEqualTo(result.get().algorithmClassification());

        TimelineTripEntity updatedTrip = tripRepository.findById(trip.getId());
        assertThat(updatedTrip.getMovementTypeSource()).isEqualTo(MovementTypeSource.AUTO);
        assertThat(updatedTrip.getMovementType()).isEqualTo(result.get().movementType());

        assertThat(overrideRepository.findByUserIdAndTripId(testUser.getId(), trip.getId())).isEmpty();
    }

    @Test
    @Transactional
    void reclassificationShouldNotOverrideManualTrips() {
        List<TimelineTripEntity> trips = getSortedTrips();
        assertThat(trips).hasSizeGreaterThanOrEqualTo(2);

        TimelineTripEntity manualTrip = trips.get(0);
        TimelineTripEntity autoTrip = trips.get(1);

        movementTypeOverrideService.setManualMovementType(testUser.getId(), manualTrip.getId(), TripType.FLIGHT);

        testUser.setTimelinePreferences(TimelinePreferences.builder()
                .carEnabled(false)
                .bicycleEnabled(false)
                .runningEnabled(false)
                .trainEnabled(false)
                .flightEnabled(false)
                .build());
        entityManager.flush();

        tripReclassificationService.reclassifyUserTrips(testUser.getId());

        TimelineTripEntity updatedManualTrip = tripRepository.findById(manualTrip.getId());
        TimelineTripEntity updatedAutoTrip = tripRepository.findById(autoTrip.getId());

        assertThat(updatedManualTrip.getMovementType()).isEqualTo("FLIGHT");
        assertThat(updatedManualTrip.getMovementTypeSource()).isEqualTo(MovementTypeSource.MANUAL);

        assertThat(updatedAutoTrip.getMovementTypeSource()).isEqualTo(MovementTypeSource.AUTO);
        assertThat(updatedAutoTrip.getMovementType()).isNotEqualTo("FLIGHT");
    }

    @Test
    @Transactional
    void shouldReapplyManualOverrideAfterFullTimelineRegeneration() {
        TimelineTripEntity trip = getSortedTrips().get(0);
        movementTypeOverrideService.setManualMovementType(testUser.getId(), trip.getId(), TripType.TRAIN);
        Long originalTripId = trip.getId();

        timelineGenerationService.regenerateFullTimeline(testUser.getId());

        List<TimelineTripEntity> regeneratedTrips = getSortedTrips();
        assertThat(regeneratedTrips).isNotEmpty();
        assertThat(regeneratedTrips.stream().map(TimelineTripEntity::getId)).doesNotContain(originalTripId);

        assertThat(regeneratedTrips)
                .anyMatch(t -> "TRAIN".equals(t.getMovementType()) && t.getMovementTypeSource() == MovementTypeSource.MANUAL);

        assertThat(overrideRepository.findByUserId(testUser.getId()))
                .anyMatch(override -> override.getTrip() != null && "TRAIN".equals(override.getMovementType()));
    }

    @Test
    @Transactional
    void classificationDetailsShouldExposeEffectiveAndAlgorithmClassification() {
        TimelineTripEntity trip = getSortedTrips().get(0);
        movementTypeOverrideService.setManualMovementType(testUser.getId(), trip.getId(), TripType.FLIGHT);

        var detailsOpt = tripClassificationDetailsService.getTripClassificationDetails(trip.getId(), testUser.getId());

        assertThat(detailsOpt).isPresent();
        var details = detailsOpt.get();

        assertThat(details.currentClassification()).isEqualTo("FLIGHT");
        assertThat(details.movementTypeSource()).isEqualTo("MANUAL");
        assertThat(details.algorithmClassification()).isNotBlank();
        assertThat(details.finalReason()).contains("Manual override is active");
        assertThat(details.steps())
                .anyMatch(step -> step.tripType().equals(details.algorithmClassification()));
    }

    @Test
    @Transactional
    void shouldNotReapplyOverrideWhenCandidateTripShapeChangedTooMuch() {
        TimelineTripEntity originalTrip = getSortedTrips().get(0);
        movementTypeOverrideService.setManualMovementType(testUser.getId(), originalTrip.getId(), TripType.CAR);

        var overrideBefore = overrideRepository.findByUserIdAndTripId(testUser.getId(), originalTrip.getId());
        assertThat(overrideBefore).isPresent();
        Instant originalOverrideTimestamp = overrideBefore.get().getSourceTripTimestamp();

        // Simulate rebuild replacement with a materially different merged trip.
        tripRepository.delete("user.id = ?1", testUser.getId());
        entityManager.flush();

        TimelineTripEntity mergedTrip = TimelineTripEntity.builder()
                .user(testUser)
                .timestamp(originalTrip.getTimestamp().minusSeconds(44 * 60)) // 44 min earlier
                .tripDuration(originalTrip.getTripDuration() * 3) // major duration change
                .distanceMeters((long) (originalTrip.getDistanceMeters() * 1.7)) // major distance change
                .startPoint(GeoUtils.createPoint(
                        originalTrip.getStartPoint().getX() - 0.007,
                        originalTrip.getStartPoint().getY() + 0.002))
                .endPoint(originalTrip.getEndPoint())
                .movementType("UNKNOWN")
                .movementTypeSource(MovementTypeSource.AUTO)
                .build();
        tripRepository.persist(mergedTrip);
        entityManager.flush();

        int appliedCount = movementTypeOverrideService.reapplyManualOverrides(testUser.getId());
        assertThat(appliedCount).isZero();

        clearPersistenceContext();
        TimelineTripEntity persistedMergedTrip = tripRepository.findById(mergedTrip.getId());
        assertThat(persistedMergedTrip.getMovementTypeSource()).isEqualTo(MovementTypeSource.AUTO);

        var overrides = overrideRepository.findByUserId(testUser.getId());
        assertThat(overrides).hasSize(1);
        assertThat(overrides.get(0).getTrip()).isNull();
        assertThat(overrides.get(0).getSourceTripTimestamp()).isEqualTo(originalOverrideTimestamp);
    }

    @Test
    @Transactional
    void shouldNotReapplyOverrideWhenTimestampDeltaExceedsThreshold() {
        TimelineTripEntity source = getSortedTrips().get(0);
        movementTypeOverrideService.setManualMovementType(testUser.getId(), source.getId(), TripType.CAR);

        replaceTripsWithCandidates(List.of(
                buildCandidateFromSource(source, 46 * 60L, 0, 0, 0, 0, 1.0, 1.0)
        ));

        int appliedCount = movementTypeOverrideService.reapplyManualOverrides(testUser.getId());
        assertThat(appliedCount).isZero();
        assertOverrideUnmatchedForMovementType("CAR");
    }

    @Test
    @Transactional
    void shouldNotReapplyOverrideWhenStartPointTooFar() {
        TimelineTripEntity source = getSortedTrips().get(0);
        movementTypeOverrideService.setManualMovementType(testUser.getId(), source.getId(), TripType.CAR);

        replaceTripsWithCandidates(List.of(
                // ~0.0044 lon shift at ~40 lat is > 350m
                buildCandidateFromSource(source, 0, 0.0044, 0, 0, 0, 1.0, 1.0)
        ));

        int appliedCount = movementTypeOverrideService.reapplyManualOverrides(testUser.getId());
        assertThat(appliedCount).isZero();
        assertOverrideUnmatchedForMovementType("CAR");
    }

    @Test
    @Transactional
    void shouldNotReapplyOverrideWhenDurationRatioIsOutOfRange() {
        TimelineTripEntity source = getSortedTrips().get(0);
        movementTypeOverrideService.setManualMovementType(testUser.getId(), source.getId(), TripType.CAR);

        replaceTripsWithCandidates(List.of(
                // 2.0x exceeds MAX_DURATION_RATIO (1.8)
                buildCandidateFromSource(source, 0, 0, 0, 0, 0, 2.0, 1.0)
        ));

        int appliedCount = movementTypeOverrideService.reapplyManualOverrides(testUser.getId());
        assertThat(appliedCount).isZero();
        assertOverrideUnmatchedForMovementType("CAR");
    }

    @Test
    @Transactional
    void shouldNotReapplyOverrideWhenDistanceRatioIsOutOfRange() {
        TimelineTripEntity source = getSortedTrips().get(0);
        movementTypeOverrideService.setManualMovementType(testUser.getId(), source.getId(), TripType.CAR);

        replaceTripsWithCandidates(List.of(
                // 2.0x exceeds MAX_DISTANCE_RATIO (1.8)
                buildCandidateFromSource(source, 0, 0, 0, 0, 0, 1.0, 2.0)
        ));

        int appliedCount = movementTypeOverrideService.reapplyManualOverrides(testUser.getId());
        assertThat(appliedCount).isZero();
        assertOverrideUnmatchedForMovementType("CAR");
    }

    @Test
    @Transactional
    void shouldSelectBestCandidateWhenMultipleTripsMatchThresholds() {
        TimelineTripEntity source = getSortedTrips().get(0);
        movementTypeOverrideService.setManualMovementType(testUser.getId(), source.getId(), TripType.CAR);

        TimelineTripEntity bestCandidate = buildCandidateFromSource(
                source, 60, 0.00008, 0.00005, 0.00005, 0.00008, 1.02, 1.01
        );
        TimelineTripEntity weakerCandidate = buildCandidateFromSource(
                source, 35 * 60L, 0.0021, 0.0018, 0.0017, 0.0019, 1.5, 1.45
        );

        replaceTripsWithCandidates(List.of(bestCandidate, weakerCandidate));

        int appliedCount = movementTypeOverrideService.reapplyManualOverrides(testUser.getId());
        assertThat(appliedCount).isEqualTo(1);

        clearPersistenceContext();
        TimelineTripEntity persistedBest = tripRepository.findById(bestCandidate.getId());
        TimelineTripEntity persistedWeaker = tripRepository.findById(weakerCandidate.getId());

        assertThat(persistedBest.getMovementTypeSource()).isEqualTo(MovementTypeSource.MANUAL);
        assertThat(persistedBest.getMovementType()).isEqualTo("CAR");
        assertThat(persistedWeaker.getMovementTypeSource()).isEqualTo(MovementTypeSource.AUTO);
    }

    @Test
    @Transactional
    void shouldNotReuseSingleCandidateForMultipleOverrides() {
        List<TimelineTripEntity> trips = getSortedTrips();
        TimelineTripEntity first = trips.get(0);
        TimelineTripEntity second = trips.get(1);

        movementTypeOverrideService.setManualMovementType(testUser.getId(), first.getId(), TripType.CAR);
        movementTypeOverrideService.setManualMovementType(testUser.getId(), second.getId(), TripType.TRAIN);

        var overrides = overrideRepository.findByUserId(testUser.getId());
        assertThat(overrides).hasSize(2);

        // Force both overrides to target the same anchor to create matching contention.
        var canonical = overrides.stream()
                .filter(o -> "CAR".equals(o.getMovementType()))
                .findFirst()
                .orElseThrow();
        overrides.stream()
                .filter(o -> "TRAIN".equals(o.getMovementType()))
                .findFirst()
                .ifPresent(other -> {
                    other.setSourceTripTimestamp(canonical.getSourceTripTimestamp());
                    other.setSourceTripDurationSeconds(canonical.getSourceTripDurationSeconds());
                    other.setSourceDistanceMeters(canonical.getSourceDistanceMeters());
                    other.setSourceStartLatitude(canonical.getSourceStartLatitude());
                    other.setSourceStartLongitude(canonical.getSourceStartLongitude());
                    other.setSourceEndLatitude(canonical.getSourceEndLatitude());
                    other.setSourceEndLongitude(canonical.getSourceEndLongitude());
                });
        entityManager.flush();

        replaceTripsWithCandidates(List.of(
                buildCandidateFromSource(first, 30, 0.00005, 0.00005, 0.00005, 0.00005, 1.0, 1.0)
        ));

        int appliedCount = movementTypeOverrideService.reapplyManualOverrides(testUser.getId());
        assertThat(appliedCount).isEqualTo(1);

        clearPersistenceContext();
        var updatedOverrides = overrideRepository.findByUserId(testUser.getId());
        assertThat(updatedOverrides.stream().filter(o -> o.getTrip() != null).toList()).hasSize(1);
        assertThat(updatedOverrides.stream().filter(o -> o.getTrip() == null).toList()).hasSize(1);
    }

    @Test
    @Transactional
    void shouldRejectManualUpdateForTripOwnedByAnotherUser() {
        UserEntity otherUser = new UserEntity();
        otherUser.setEmail("other-owner@geopulse.app");
        otherUser.setFullName("Other Owner");
        otherUser.setPasswordHash("test");
        userRepository.persist(otherUser);

        TimelineTripEntity foreignTrip = TimelineTripEntity.builder()
                .user(otherUser)
                .timestamp(Instant.parse("2026-01-01T10:00:00Z"))
                .tripDuration(1200)
                .distanceMeters(1500)
                .startPoint(GeoUtils.createPoint(25.0, 49.0))
                .endPoint(GeoUtils.createPoint(25.01, 49.01))
                .movementType("UNKNOWN")
                .movementTypeSource(MovementTypeSource.AUTO)
                .build();
        tripRepository.persist(foreignTrip);
        entityManager.flush();

        assertThat(movementTypeOverrideService.setManualMovementType(testUser.getId(), foreignTrip.getId(), TripType.CAR))
                .isEmpty();
        assertThat(movementTypeOverrideService.resetToAutomaticMovementType(testUser.getId(), foreignTrip.getId()))
                .isEmpty();
    }

    @Test
    @Transactional
    void shouldKeepSourceAnchorsImmutableAfterSuccessfulRematch() {
        TimelineTripEntity source = getSortedTrips().get(0);
        movementTypeOverrideService.setManualMovementType(testUser.getId(), source.getId(), TripType.CAR);

        var overrideBefore = overrideRepository.findByUserIdAndTripId(testUser.getId(), source.getId()).orElseThrow();
        Instant sourceTimestamp = overrideBefore.getSourceTripTimestamp();
        long sourceDuration = overrideBefore.getSourceTripDurationSeconds();
        long sourceDistance = overrideBefore.getSourceDistanceMeters();
        double sourceStartLat = overrideBefore.getSourceStartLatitude();
        double sourceStartLon = overrideBefore.getSourceStartLongitude();
        double sourceEndLat = overrideBefore.getSourceEndLatitude();
        double sourceEndLon = overrideBefore.getSourceEndLongitude();

        TimelineTripEntity rematchedCandidate = buildCandidateFromSource(
                source, 3 * 60L, 0.0002, 0.0001, 0.0002, 0.0001, 1.1, 1.05
        );
        replaceTripsWithCandidates(List.of(rematchedCandidate));

        int appliedCount = movementTypeOverrideService.reapplyManualOverrides(testUser.getId());
        assertThat(appliedCount).isEqualTo(1);

        clearPersistenceContext();
        var persistedOverride = overrideRepository.findByUserId(testUser.getId()).get(0);
        assertThat(persistedOverride.getTrip()).isNotNull();
        assertThat(persistedOverride.getTrip().getId()).isEqualTo(rematchedCandidate.getId());
        assertThat(persistedOverride.getSourceTripTimestamp()).isEqualTo(sourceTimestamp);
        assertThat(persistedOverride.getSourceTripDurationSeconds()).isEqualTo(sourceDuration);
        assertThat(persistedOverride.getSourceDistanceMeters()).isEqualTo(sourceDistance);
        assertThat(persistedOverride.getSourceStartLatitude()).isEqualTo(sourceStartLat);
        assertThat(persistedOverride.getSourceStartLongitude()).isEqualTo(sourceStartLon);
        assertThat(persistedOverride.getSourceEndLatitude()).isEqualTo(sourceEndLat);
        assertThat(persistedOverride.getSourceEndLongitude()).isEqualTo(sourceEndLon);
    }

    private List<TimelineTripEntity> getSortedTrips() {
        return tripRepository.findByUser(testUser.getId()).stream()
                .sorted(Comparator.comparing(TimelineTripEntity::getTimestamp))
                .toList();
    }

    private void replaceTripsWithCandidates(List<TimelineTripEntity> candidates) {
        tripRepository.delete("user.id = ?1", testUser.getId());
        entityManager.flush();
        candidates.forEach(tripRepository::persist);
        entityManager.flush();
    }

    private TimelineTripEntity buildCandidateFromSource(TimelineTripEntity source,
                                                        long timestampShiftSeconds,
                                                        double startLonShift,
                                                        double startLatShift,
                                                        double endLonShift,
                                                        double endLatShift,
                                                        double durationMultiplier,
                                                        double distanceMultiplier) {
        return TimelineTripEntity.builder()
                .user(testUser)
                .timestamp(source.getTimestamp().plusSeconds(timestampShiftSeconds))
                .tripDuration(Math.max(1, Math.round(source.getTripDuration() * durationMultiplier)))
                .distanceMeters(Math.max(1, Math.round(source.getDistanceMeters() * distanceMultiplier)))
                .startPoint(GeoUtils.createPoint(
                        source.getStartPoint().getX() + startLonShift,
                        source.getStartPoint().getY() + startLatShift))
                .endPoint(GeoUtils.createPoint(
                        source.getEndPoint().getX() + endLonShift,
                        source.getEndPoint().getY() + endLatShift))
                .movementType("UNKNOWN")
                .movementTypeSource(MovementTypeSource.AUTO)
                .build();
    }

    private void assertOverrideUnmatchedForMovementType(String movementType) {
        clearPersistenceContext();
        var overrides = overrideRepository.findByUserId(testUser.getId());
        assertThat(overrides)
                .filteredOn(o -> movementType.equals(o.getMovementType()))
                .singleElement()
                .satisfies(override -> assertThat(override.getTrip()).isNull());
    }

    private void clearPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }

    private void createGpsScenarioWithTwoTrips(UserEntity user) {
        List<GpsPointEntity> allPoints = new ArrayList<>();

        // Home stay
        allPoints.addAll(createStationaryPoints(
                user, HOME_LAT, HOME_LON,
                "2024-08-15T08:00:00Z", "2024-08-15T09:00:00Z", 5
        ));

        // Home -> Office trip
        allPoints.addAll(createMovingPoints(
                user, HOME_LAT, HOME_LON, OFFICE_LAT, OFFICE_LON,
                "2024-08-15T09:01:00Z", "2024-08-15T09:30:00Z"
        ));

        // Office stay
        allPoints.addAll(createStationaryPoints(
                user, OFFICE_LAT, OFFICE_LON,
                "2024-08-15T09:31:00Z", "2024-08-15T11:00:00Z", 5
        ));

        // Office -> Home trip
        allPoints.addAll(createMovingPoints(
                user, OFFICE_LAT, OFFICE_LON, HOME_LAT, HOME_LON,
                "2024-08-15T11:01:00Z", "2024-08-15T11:30:00Z"
        ));

        // Home stay
        allPoints.addAll(createStationaryPoints(
                user, HOME_LAT, HOME_LON,
                "2024-08-15T11:31:00Z", "2024-08-15T13:00:00Z", 5
        ));

        allPoints.forEach(gpsPointRepository::persist);
        entityManager.flush();
    }

    private List<GpsPointEntity> createStationaryPoints(UserEntity user, double lat, double lon,
                                                        String startTime, String endTime, int intervalMinutes) {
        List<GpsPointEntity> points = new ArrayList<>();
        Instant start = Instant.parse(startTime);
        Instant end = Instant.parse(endTime);

        Instant current = start;
        while (current.isBefore(end) || current.equals(end)) {
            GpsPointEntity point = new GpsPointEntity();
            point.setUser(user);
            point.setTimestamp(current);
            point.setCoordinates(createPoint(lon, lat));
            point.setAccuracy(5.0);
            point.setVelocity(0.0);

            points.add(point);
            current = current.plusSeconds(intervalMinutes * 60L);
        }

        return points;
    }

    private List<GpsPointEntity> createMovingPoints(UserEntity user,
                                                    double startLat, double startLon,
                                                    double endLat, double endLon,
                                                    String startTime, String endTime) {
        List<GpsPointEntity> points = new ArrayList<>();
        Instant start = Instant.parse(startTime);
        Instant end = Instant.parse(endTime);

        long totalMinutes = java.time.Duration.between(start, end).toMinutes();
        int numPoints = Math.max(2, (int) (totalMinutes / 2));

        for (int i = 0; i < numPoints; i++) {
            double progress = (double) i / (numPoints - 1);
            double lat = startLat + (endLat - startLat) * progress;
            double lon = startLon + (endLon - startLon) * progress;

            Instant timestamp = start.plusSeconds((long) (totalMinutes * 60 * progress));

            GpsPointEntity point = new GpsPointEntity();
            point.setUser(user);
            point.setTimestamp(timestamp);
            point.setCoordinates(createPoint(lon, lat));
            point.setAccuracy(8.0);
            point.setVelocity(10.0);

            points.add(point);
        }

        return points;
    }

    private Point createPoint(double lon, double lat) {
        return geometryFactory.createPoint(new Coordinate(lon, lat));
    }
}
