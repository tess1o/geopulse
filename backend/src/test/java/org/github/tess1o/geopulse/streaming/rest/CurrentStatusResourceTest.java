package org.github.tess1o.geopulse.streaming.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.auth.service.ApiTokenService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.streaming.model.dto.CurrentActivityState;
import org.github.tess1o.geopulse.streaming.model.dto.CurrentStatusDTO;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.service.CurrentActivityService;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class CurrentStatusResourceTest {

    /** 13:00 local time in Kyiv (UTC+3); local midnight is 2026-05-19T21:00:00Z. */
    private static final Instant NOW = Instant.parse("2026-05-20T10:00:00Z");

    @Inject
    UserService userService;

    @Inject
    ApiTokenService apiTokenService;

    @Inject
    EntityManager entityManager;

    @Inject
    CurrentActivityService currentActivityService;

    private UUID userId;
    private String readToken;

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            UserEntity user = userService.registerUser(TestIds.uniqueEmail("current-status"), "password123",
                    "Activity User", "Europe/Kyiv");
            userId = user.getId();
            readToken = apiTokenService.createToken(userId, "ha", Instant.now().plusSeconds(3600),
                    List.of("read"), "127.0.0.1").getToken();
        });
    }

    @Test
    void statusWithoutDataIsNoData() {
        given().header("X-API-Key", readToken)
                .when().get("/api/v1/users/me/status")
                .then()
                .statusCode(200)
                .body("state", equalTo("NO_DATA"))
                .body("timezone", equalTo("Europe/Kyiv"))
                .body("today.tripCount", equalTo(0));
    }

    @Test
    void statusReportsTheCurrentStayAndTodayInTheUsersTimezone() {
        QuarkusTransaction.requiringNew().run(() -> {
            trip("2026-05-19T20:00:00Z", 900, 5_000);              // yesterday in Kyiv
            stay("2026-05-19T21:30:00Z", 37_800, "Home", 50.40, 30.50);
            trip("2026-05-20T08:00:00Z", 1_200, 3_000);
            stay("2026-05-20T08:20:00Z", 6_000, "Gym", 50.45, 30.52);
            point("2026-05-20T09:58:00Z", 50.45, 30.52);
        });

        CurrentStatusDTO status = currentActivityService.getStatus(userId, "Europe/Kyiv", NOW);

        assertThat(status.state()).isEqualTo(CurrentActivityState.STAY);
        assertThat(status.stay().placeName()).isEqualTo("Gym");
        assertThat(status.stay().since()).isEqualTo(Instant.parse("2026-05-20T08:20:00Z"));
        assertThat(status.stay().durationSeconds()).isEqualTo(6_000);
        assertThat(status.trip()).isNull();
        assertThat(status.lastPoint().ageSeconds()).isEqualTo(120);
        assertThat(status.today().date()).isEqualTo(LocalDate.parse("2026-05-20"));
        assertThat(status.today().tripCount()).isEqualTo(1);
        assertThat(status.today().distanceMeters()).isEqualTo(3_000);
        assertThat(status.today().movingSeconds()).isEqualTo(1_200);
        assertThat(status.today().stayCount()).isEqualTo(2);
        assertThat(status.today().uniquePlaces()).isEqualTo(2);
    }

    @Test
    void statusReportsATripInProgressAndAnOngoingDataGap() {
        QuarkusTransaction.requiringNew().run(() -> {
            stay("2026-05-20T07:00:00Z", 3_600, "Home", 50.40, 30.50);
            trip("2026-05-20T09:30:00Z", 1_500, 9_000);
            point("2026-05-20T09:55:00Z", 50.47, 30.60);
        });
        CurrentStatusDTO trip = currentActivityService.getStatus(userId, "Europe/Kyiv", NOW);
        assertThat(trip.state()).isEqualTo(CurrentActivityState.TRIP);
        assertThat(trip.trip().durationSeconds()).isEqualTo(1_800);
        assertThat(trip.trip().distanceMeters()).isEqualTo(9_000);

        QuarkusTransaction.requiringNew().run(() -> gap("2026-05-20T09:55:00Z", "2026-05-20T10:00:00Z"));
        CurrentStatusDTO gap = currentActivityService.getStatus(userId, "Europe/Kyiv", NOW);
        assertThat(gap.state()).isEqualTo(CurrentActivityState.DATA_GAP);
        assertThat(gap.dataGap().since()).isEqualTo(Instant.parse("2026-05-20T09:55:00Z"));
    }

    private void stay(String start, long durationSeconds, String name, double lat, double lon) {
        entityManager.persist(TimelineStayEntity.builder()
                .user(entityManager.getReference(UserEntity.class, userId))
                .timestamp(Instant.parse(start))
                .stayDuration(durationSeconds)
                .location(GeoUtils.createPoint(lon, lat))
                .locationName(name)
                .build());
    }

    private void trip(String start, long durationSeconds, long distanceMeters) {
        entityManager.persist(TimelineTripEntity.builder()
                .user(entityManager.getReference(UserEntity.class, userId))
                .timestamp(Instant.parse(start))
                .tripDuration(durationSeconds)
                .distanceMeters(distanceMeters)
                .startPoint(GeoUtils.createPoint(30.50, 50.40))
                .endPoint(GeoUtils.createPoint(30.60, 50.50))
                .movementType("CAR")
                .build());
    }

    private void gap(String start, String end) {
        Instant startTime = Instant.parse(start);
        Instant endTime = Instant.parse(end);
        entityManager.persist(TimelineDataGapEntity.builder()
                .user(entityManager.getReference(UserEntity.class, userId))
                .startTime(startTime)
                .endTime(endTime)
                .durationSeconds(endTime.getEpochSecond() - startTime.getEpochSecond())
                .createdAt(Instant.now())
                .build());
    }

    private void point(String timestamp, double lat, double lon) {
        GpsPointEntity point = new GpsPointEntity();
        point.setUser(entityManager.getReference(UserEntity.class, userId));
        point.setDeviceId("test");
        point.setTimestamp(Instant.parse(timestamp));
        point.setCoordinates(GeoUtils.createPoint(lon, lat));
        point.setAccuracy(5.0);
        point.setSourceType(GpsSourceType.HOME_ASSISTANT);
        point.setCreatedAt(Instant.parse(timestamp));
        entityManager.persist(point);
    }
}
