package org.github.tess1o.geopulse.trips.rest;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.CleanupHelper;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemPriority;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemVisitSource;
import org.github.tess1o.geopulse.trips.model.entity.TripPlaceVisitMatchEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripStatus;
import org.github.tess1o.geopulse.trips.repository.TripPlanItemRepository;
import org.github.tess1o.geopulse.trips.repository.TripPlaceVisitMatchRepository;
import org.github.tess1o.geopulse.trips.repository.TripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class, restrictToAnnotatedClass = true)
@SerializedDatabaseTest
class TripVisitMatchingResourceIntegrationTest {

    @Inject
    AuthenticationService authenticationService;
    @Inject
    UserService userService;
    @Inject
    UserRepository userRepository;
    @Inject
    TripRepository tripRepository;
    @Inject
    TripPlanItemRepository tripPlanItemRepository;
    @Inject
    TripPlaceVisitMatchRepository tripPlaceVisitMatchRepository;
    @Inject
    TimelineStayRepository timelineStayRepository;
    @Inject
    CleanupHelper cleanupHelper;

    private String accessToken;
    private Long tripId;
    private Long planItemId;
    private Long stayId;

    @BeforeEach
    @Transactional
    void setUp() {
        cleanupHelper.cleanupTripWorkspaceAndUsers();

        String email = "trip-resource-" + UUID.randomUUID() + "@example.com";
        userService.registerUser(email, "password123", "Trip Resource Tester", "UTC");

        AuthResponse authResponse = authenticationService.authenticate(email, "password123");
        accessToken = authResponse.getAccessToken();

        UserEntity user = userRepository.findByEmail(email).orElseThrow();

        TripEntity trip = TripEntity.builder()
                .user(user)
                .name("API Trip")
                .startTime(Instant.parse("2026-02-01T00:00:00Z"))
                .endTime(Instant.parse("2026-02-03T00:00:00Z"))
                .status(TripStatus.COMPLETED)
                .build();
        tripRepository.persist(trip);
        tripId = trip.getId();

        TripPlanItemEntity item = TripPlanItemEntity.builder()
                .trip(trip)
                .title("London Waterloo")
                .priority(TripPlanItemPriority.MUST)
                .orderIndex(0)
                .isVisited(true)
                .visitSource(TripPlanItemVisitSource.AUTO)
                .visitConfidence(0.96)
                .build();
        item.setLatitude(51.5033);
        item.setLongitude(-0.1147);
        tripPlanItemRepository.persist(item);
        planItemId = item.getId();

        TimelineStayEntity stay = TimelineStayEntity.builder()
                .user(user)
                .timestamp(Instant.parse("2026-02-02T10:00:00Z"))
                .stayDuration(900)
                .location(GeoUtils.createPoint(-0.1148, 51.50335))
                .locationName("London Waterloo")
                .build();
        timelineStayRepository.persist(stay);
        stayId = stay.getId();

        tripPlaceVisitMatchRepository.persist(TripPlaceVisitMatchEntity.builder()
                .trip(trip)
                .planItem(item)
                .stay(stay)
                .distanceMeters(15.0)
                .dwellSeconds(900L)
                .confidence(0.96)
                .decision("AUTO_MATCHED")
                .build());
    }

    @Test
    void getVisitSuggestions_shouldReturnStoredSuggestions() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .queryParam("applyAuto", true)
                .when()
                .get("/api/trips/{tripId}/visit-suggestions", tripId)
                .then()
                .statusCode(200)
                .body("status", equalTo("success"))
                .body("data[0].planItemId", equalTo(planItemId.intValue()))
                .body("data[0].matchedStayId", equalTo(stayId.intValue()))
                .body("data[0].decision", equalTo("AUTO_MATCHED"))
                .body("data[0].applied", equalTo(true));
    }

    @Test
    void getVisitSuggestions_shouldReturnNotFoundForUnknownTrip() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .get("/api/trips/{tripId}/visit-suggestions", 999999L)
                .then()
                .statusCode(404)
                .body("status", equalTo("error"))
                .body("message", equalTo("Trip not found"));
    }

}
