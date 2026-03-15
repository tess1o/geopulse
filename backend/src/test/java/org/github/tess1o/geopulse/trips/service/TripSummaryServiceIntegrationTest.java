package org.github.tess1o.geopulse.trips.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.CleanupHelper;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.periods.repository.PeriodTagRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.trips.model.dto.TripSummaryDto;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemPriority;
import org.github.tess1o.geopulse.trips.model.entity.TripStatus;
import org.github.tess1o.geopulse.trips.repository.TripPlanItemRepository;
import org.github.tess1o.geopulse.trips.repository.TripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class, restrictToAnnotatedClass = true)
@SerializedDatabaseTest
class TripSummaryServiceIntegrationTest {

    @Inject
    TripSummaryService tripSummaryService;
    @Inject
    TripRepository tripRepository;
    @Inject
    TripPlanItemRepository tripPlanItemRepository;
    @Inject
    TimelineStayRepository timelineStayRepository;
    @Inject
    TimelineTripRepository timelineTripRepository;
    @Inject
    FavoritesRepository favoritesRepository;
    @Inject
    PeriodTagRepository periodTagRepository;
    @Inject
    UserRepository userRepository;
    @Inject
    UserService userService;
    @Inject
    CleanupHelper cleanupHelper;

    private UUID userId;
    private Long tripId;

    @BeforeEach
    @Transactional
    void setUp() {
        cleanupHelper.cleanupTripWorkspaceAndUsers();

        String email = "trip-summary-" + UUID.randomUUID() + "@example.com";
        UserEntity user = userService.registerUser(email, "password123", "Trip Summary Tester", "UTC");
        userId = user.getId();

        TripEntity trip = TripEntity.builder()
                .user(user)
                .name("Summary Trip")
                .startTime(Instant.parse("2026-01-01T00:00:00Z"))
                .endTime(Instant.parse("2026-01-03T00:00:00Z"))
                .status(TripStatus.COMPLETED)
                .build();
        tripRepository.persist(trip);
        tripId = trip.getId();
    }

    @Test
    @Transactional
    void getSummary_shouldAggregatePlanTimelineDistanceAndActualPlaces() {
        TripEntity trip = tripRepository.findById(tripId);
        UserEntity user = userRepository.findById(userId);

        tripPlanItemRepository.persist(TripPlanItemEntity.builder()
                .trip(trip)
                .title("Place 1")
                .priority(TripPlanItemPriority.MUST)
                .orderIndex(0)
                .isVisited(true)
                .build());
        tripPlanItemRepository.persist(TripPlanItemEntity.builder()
                .trip(trip)
                .title("Place 2")
                .priority(TripPlanItemPriority.OPTIONAL)
                .orderIndex(1)
                .isVisited(true)
                .build());
        tripPlanItemRepository.persist(TripPlanItemEntity.builder()
                .trip(trip)
                .title("Place 3")
                .priority(TripPlanItemPriority.OPTIONAL)
                .orderIndex(2)
                .isVisited(false)
                .build());

        FavoritesEntity favA = FavoritesEntity.builder()
                .user(user)
                .name("Hotel A")
                .type(FavoriteLocationType.POINT)
                .city("Madrid")
                .country("Spain")
                .geometry(GeoUtils.createPoint(-3.7038, 40.4168))
                .build();
        favoritesRepository.persist(favA);

        FavoritesEntity favB = FavoritesEntity.builder()
                .user(user)
                .name("Museum B")
                .type(FavoriteLocationType.POINT)
                .city("Madrid")
                .country("Spain")
                .geometry(GeoUtils.createPoint(-3.6883, 40.4230))
                .build();
        favoritesRepository.persist(favB);

        timelineStayRepository.persist(TimelineStayEntity.builder()
                .user(user)
                .timestamp(Instant.parse("2026-01-01T08:00:00Z"))
                .stayDuration(3600)
                .location(GeoUtils.createPoint(-3.7038, 40.4168))
                .locationName("Hotel A")
                .favoriteLocation(favA)
                .build());
        timelineStayRepository.persist(TimelineStayEntity.builder()
                .user(user)
                .timestamp(Instant.parse("2026-01-01T13:00:00Z"))
                .stayDuration(1800)
                .location(GeoUtils.createPoint(-3.7039, 40.4169))
                .locationName("Hotel A")
                .favoriteLocation(favA)
                .build());
        timelineStayRepository.persist(TimelineStayEntity.builder()
                .user(user)
                .timestamp(Instant.parse("2026-01-02T11:00:00Z"))
                .stayDuration(2700)
                .location(GeoUtils.createPoint(-3.6883, 40.4230))
                .locationName("Museum B")
                .favoriteLocation(favB)
                .build());

        timelineTripRepository.persist(TimelineTripEntity.builder()
                .user(user)
                .timestamp(Instant.parse("2026-01-01T09:30:00Z"))
                .tripDuration(300)
                .distanceMeters(1000)
                .startPoint(GeoUtils.createPoint(-3.7038, 40.4168))
                .endPoint(GeoUtils.createPoint(-3.7000, 40.4200))
                .movementType("WALK")
                .build());
        timelineTripRepository.persist(TimelineTripEntity.builder()
                .user(user)
                .timestamp(Instant.parse("2026-01-02T10:30:00Z"))
                .tripDuration(600)
                .distanceMeters(2300)
                .startPoint(GeoUtils.createPoint(-3.7000, 40.4200))
                .endPoint(GeoUtils.createPoint(-3.6883, 40.4230))
                .movementType("CAR")
                .build());

        TripSummaryDto summary = tripSummaryService.getSummary(userId, tripId);

        assertThat(summary.getTripId()).isEqualTo(tripId);
        assertThat(summary.getTripName()).isEqualTo("Summary Trip");
        assertThat(summary.getStatus()).isEqualTo(TripStatus.COMPLETED);
        assertThat(summary.getPlanItemsTotal()).isEqualTo(3);
        assertThat(summary.getPlanItemsVisited()).isEqualTo(2);
        assertThat(summary.getPlanCompletionRate()).isEqualTo(66.66666666666667);
        assertThat(summary.getTimelineStays()).isEqualTo(3);
        assertThat(summary.getTimelineTrips()).isEqualTo(2);
        assertThat(summary.getTotalDistanceMeters()).isEqualTo(3300);
        assertThat(summary.getTotalTripDurationSeconds()).isEqualTo(900);
        assertThat(summary.getActualPlacesCount()).isEqualTo(2);
    }

}
