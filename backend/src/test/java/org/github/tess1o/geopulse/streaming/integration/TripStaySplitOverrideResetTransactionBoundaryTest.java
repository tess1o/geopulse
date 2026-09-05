package org.github.tess1o.geopulse.streaming.integration;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.exception.TimelineGenerationLockException;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.streaming.model.dto.TripStaySplitRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripStaySplitOverrideEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripStaySplitOverrideRepository;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.TripStaySplitOverrideService;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.TimelineStatus;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class TripStaySplitOverrideResetTransactionBoundaryTest {

    @Inject
    StreamingTimelineGenerationService timelineGenerationService;

    @Inject
    TimelineTripStaySplitOverrideRepository overrideRepository;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    FavoritesRepository favoritesRepository;

    @Inject
    TripStaySplitOverrideService tripStaySplitOverrideService;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    private UUID userId;
    private Long overrideId;

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            UserEntity user = new UserEntity();
            user.setEmail(TestIds.uniqueEmail("trip-split-reset"));
            user.setFullName("Trip Split Reset");
            user.setPasswordHash("test");
            user.setTimelineStatus(TimelineStatus.IDLE);
            userRepository.persist(user);

            Instant tripStart = Instant.parse("2026-01-10T10:00:00Z");
            TimelineTripStaySplitOverrideEntity override = TimelineTripStaySplitOverrideEntity.builder()
                    .user(user)
                    .stayStartTime(tripStart.plusSeconds(120))
                    .stayEndTime(tripStart.plusSeconds(240))
                    .anchorTimestamp(tripStart.plusSeconds(180))
                    .stayLatitude(50.4501)
                    .stayLongitude(30.5234)
                    .stayLocationName("Test stop")
                    .stayLocationSource(LocationSource.HISTORICAL)
                    .sourceTripTimestamp(tripStart)
                    .sourceTripDurationSeconds(360)
                    .sourceDistanceMeters(1_000)
                    .sourceStartLatitude(50.4400)
                    .sourceStartLongitude(30.5100)
                    .sourceEndLatitude(50.4600)
                    .sourceEndLongitude(30.5400)
                    .build();
            overrideRepository.persist(override);
            entityManager.flush();

            userId = user.getId();
            overrideId = override.getId();
        });
    }

    @Test
    void keepsTheOverrideWhenRegenerationCannotAcquireTheTimelineLock() {
        QuarkusTransaction.requiringNew().run(() ->
                userRepository.findById(userId).setTimelineStatus(TimelineStatus.PROCESSING));

        assertThatThrownBy(() -> timelineGenerationService.resetTripStaySplitOverride(userId, overrideId))
                .isInstanceOf(TimelineGenerationLockException.class);

        boolean overrideExists = QuarkusTransaction.requiringNew().call(() ->
                overrideRepository.findByIdAndUserId(overrideId, userId).isPresent());
        assertThat(overrideExists).isTrue();
    }

    @Test
    @Transactional
    void replaysAPersistedSplitAfterTimelineRowsAreRebuilt() {
        Instant tripStart = Instant.parse("2026-02-10T10:00:00Z");
        UserEntity user = userRepository.findById(userId);
        createFavorite(user, 50.4501, 30.5234);
        createTripPoints(user, tripStart);

        TimelineTripEntity originalTrip = sourceTrip(user, tripStart);
        tripRepository.persist(originalTrip);
        entityManager.flush();

        TripStaySplitRequest request = new TripStaySplitRequest();
        request.setStayStartTime(tripStart.plusSeconds(120));
        request.setStayEndTime(tripStart.plusSeconds(240));
        request.setAnchorTimestamp(tripStart.plusSeconds(180));
        request.setLatitude(50.4501);
        request.setLongitude(30.5234);

        var split = tripStaySplitOverrideService.splitTrip(userId, originalTrip.getId(), request).orElseThrow();
        assertThat(split.overrideId()).isNotNull();
        assertThat(tripRepository.findByUser(userId)).hasSize(2);
        assertThat(stayRepository.findByUserAndDateRange(userId, tripStart, tripStart.plusSeconds(360))).hasSize(1);

        tripRepository.delete("user.id = ?1", userId);
        stayRepository.delete("user.id = ?1", userId);
        entityManager.flush();
        entityManager.clear();

        tripRepository.persist(sourceTrip(entityManager.getReference(UserEntity.class, userId), tripStart));
        entityManager.flush();

        assertThat(tripStaySplitOverrideService.reapplyManualOverrides(userId)).isEqualTo(1);
        assertThat(tripRepository.findByUser(userId)).hasSize(2);
        assertThat(stayRepository.findByUserAndDateRange(userId, tripStart, tripStart.plusSeconds(360))).hasSize(1);
    }

    private void createFavorite(UserEntity user, double latitude, double longitude) {
        favoritesRepository.persist(FavoritesEntity.builder()
                .user(user)
                .name("Manual split stop")
                .type(FavoriteLocationType.POINT)
                .geometry(GeoUtils.createPoint(longitude, latitude))
                .mergeImpact(Boolean.FALSE)
                .build());
    }

    private void createTripPoints(UserEntity user, Instant start) {
        createGpsPoint(user, start, 50.4400, 30.5100);
        createGpsPoint(user, start.plusSeconds(60), 50.4450, 30.5160);
        createGpsPoint(user, start.plusSeconds(120), 50.4501, 30.5234);
        createGpsPoint(user, start.plusSeconds(180), 50.45011, 30.52342);
        createGpsPoint(user, start.plusSeconds(240), 50.45008, 30.52338);
        createGpsPoint(user, start.plusSeconds(300), 50.4550, 30.5300);
        createGpsPoint(user, start.plusSeconds(360), 50.4600, 30.5400);
    }

    private void createGpsPoint(UserEntity user, Instant timestamp, double latitude, double longitude) {
        GpsPointEntity point = new GpsPointEntity();
        point.setUser(user);
        point.setTimestamp(timestamp);
        point.setCoordinates(GeoUtils.createPoint(longitude, latitude));
        point.setAccuracy(5.0);
        point.setVelocity(10.0);
        gpsPointRepository.persist(point);
    }

    private TimelineTripEntity sourceTrip(UserEntity user, Instant start) {
        return TimelineTripEntity.builder()
                .user(user)
                .timestamp(start)
                .tripDuration(360)
                .distanceMeters(4_000)
                .startPoint(GeoUtils.createPoint(30.5100, 50.4400))
                .endPoint(GeoUtils.createPoint(30.5400, 50.4600))
                .movementType("CAR")
                .build();
    }
}
