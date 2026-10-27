package org.github.tess1o.geopulse.digest.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.testsupport.TimelineSeeder;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;

/**
 * HTTP contract for {@code /api/v1/digests/yearly-replay}. The owner lives in Europe/Kyiv (UTC+2 in winter), so
 * trips just before and after midnight UTC on New Year's Eve land in different calendar years than in UTC.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
class DigestReplayContractTest {

    private static final String REPLAY = "/api/v1/digests/yearly-replay";
    private static final String PASSWORD = "password123";

    @Inject UserService userService;
    @Inject AuthenticationService authenticationService;
    @Inject TimelineSeeder seed;
    @Inject TimelineTripRepository tripRepository;

    private UserEntity owner;
    private String ownerToken;

    @BeforeEach
    void setUp() {
        owner = register("replay-owner", "Europe/Kyiv");
        ownerToken = token(owner);
    }

    @Test
    void picksTheLongestTripPerLocalMonthForTheCurrentUserOnly() {
        TimelineTripEntity localJanuaryFirst = seed.trip(owner, Instant.parse("2025-12-31T22:30:00Z"), 7_000, 1_800, "CAR");
        seed.trip(owner, Instant.parse("2026-01-20T10:00:00Z"), 5_000, 1_200, "WALK");
        TimelineTripEntity march = seed.trip(owner, Instant.parse("2026-03-10T10:00:00Z"), 4_000, 3_600, "BICYCLE");
        TimelineTripEntity lastLocalEvening = seed.trip(owner, Instant.parse("2026-12-31T21:30:00Z"), 3_000, 600, "CAR");
        seed.trip(owner, Instant.parse("2026-12-31T22:00:00Z"), 99_000, 600, "CAR"); // Jan 1 2027 00:00 local
        seed.trip(owner, Instant.parse("2025-12-31T21:30:00Z"), 99_000, 600, "CAR"); // Dec 31 2025 23:30 local

        UserEntity stranger = register("replay-stranger", "Europe/Kyiv");
        seed.trip(stranger, Instant.parse("2026-02-10T10:00:00Z"), 50_000, 1_800, "CAR");

        Response response = authenticated(ownerToken).queryParam("year", 2026).when().get(REPLAY);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getInt("year")).isEqualTo(2026);
        assertThat(response.jsonPath().getList("trips.id", Long.class))
                .as("chronological, one per local month, no trips from another user or another local year")
                .containsExactly(localJanuaryFirst.getId(), march.getId(), lastLocalEvening.getId());
    }

    @Test
    void buildsPathsFromGpsPointsAndFallsBackToTripEndpoints() {
        TimelineTripEntity withGps = seed.trip(owner, Instant.parse("2026-04-10T10:00:00Z"), 4_000, 3_600, "CAR");
        seed.gpsPoint(owner, Instant.parse("2026-04-10T09:50:00Z"), 10.0, 10.0, 5.0); // before the trip
        seed.gpsPoint(owner, Instant.parse("2026-04-10T10:00:00Z"), 50.4501, 30.5234, 5.0);
        seed.gpsPoint(owner, Instant.parse("2026-04-10T10:20:00Z"), 50.4700, 30.6000, 5.0);
        seed.gpsPoint(owner, Instant.parse("2026-04-10T10:40:00Z"), 50.5200, 30.5000, 5.0);
        seed.gpsPoint(owner, Instant.parse("2026-04-10T11:00:00Z"), 50.5500, 30.7000, 5.0);
        seed.trip(owner, Instant.parse("2026-05-10T10:00:00Z"), 2_000, 600, "WALK");

        Response response = authenticated(ownerToken).queryParam("year", 2026).when().get(REPLAY);

        assertThat(response.statusCode()).isEqualTo(200);
        List<Map<String, Object>> trips = response.jsonPath().getList("trips");
        assertThat(trips).hasSize(2);

        Map<String, Object> april = trips.get(0);
        assertThat(((Number) april.get("id")).longValue()).isEqualTo(withGps.getId());
        List<Map<String, Object>> aprilPath = path(april);
        assertThat(aprilPath).hasSizeGreaterThanOrEqualTo(2);
        assertThat(aprilPath).extracting(point -> ((Number) point.get("latitude")).doubleValue())
                .as("only points inside the trip window").doesNotContain(10.0);
        assertThat(((Number) aprilPath.getFirst().get("latitude")).doubleValue()).isCloseTo(50.4501, offset(1e-5));
        assertThat(((Number) aprilPath.getLast().get("longitude")).doubleValue()).isCloseTo(30.7000, offset(1e-5));

        List<Map<String, Object>> mayPath = path(trips.get(1));
        assertThat(mayPath).as("no GPS points: the trip endpoints").hasSize(2);
        assertThat(((Number) mayPath.getFirst().get("latitude")).doubleValue()).isCloseTo(50.4501, offset(1e-5));
        assertThat(((Number) mayPath.getLast().get("longitude")).doubleValue()).isCloseTo(30.5300, offset(1e-5));
    }

    @Test
    void omitsTripsWithoutTwoDistinctCoordinates() {
        TimelineTripEntity collapsed = seed.trip(owner, Instant.parse("2026-06-10T10:00:00Z"), 4_000, 600, "CAR");
        QuarkusTransaction.requiringNew().run(() -> {
            TimelineTripEntity trip = tripRepository.findById(collapsed.getId());
            trip.setEndPoint(trip.getStartPoint());
        });
        Instant start = Instant.parse("2026-06-10T10:00:00Z");
        seed.gpsPoint(owner, start, 50.4501, 30.5234, 5.0);
        seed.gpsPoint(owner, start.plusSeconds(300), 50.4501, 30.5234, 5.0);
        TimelineTripEntity july = seed.trip(owner, Instant.parse("2026-07-10T10:00:00Z"), 1_000, 600, "WALK");

        Response response = authenticated(ownerToken).queryParam("year", 2026).when().get(REPLAY);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getList("trips.id", Long.class)).containsExactly(july.getId());
    }

    @Test
    void answersAnEmptyYearWithNoTrips() {
        Response response = authenticated(ownerToken).queryParam("year", 2030).when().get(REPLAY);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getInt("year")).isEqualTo(2030);
        assertThat(response.jsonPath().getList("trips")).isEmpty();
    }

    @Test
    void rejectsYearsOutsideTheDigestRangeAndAnonymousCallers() {
        assertProblemEnvelope(authenticated(ownerToken).queryParam("year", 1999).when().get(REPLAY),
                400, "INVALID_DIGEST_YEAR");
        assertProblemEnvelope(authenticated(ownerToken).queryParam("year", 2101).when().get(REPLAY),
                400, "INVALID_DIGEST_YEAR");

        assertThat(given().queryParam("year", 2026).when().get(REPLAY).statusCode()).isEqualTo(401);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> path(Map<String, Object> trip) {
        return (List<Map<String, Object>>) trip.get("path");
    }

    private UserEntity register(String prefix, String timezone) {
        return userService.registerUser(TestIds.uniqueEmail(prefix), PASSWORD, prefix + " User", timezone);
    }

    private String token(UserEntity user) {
        return authenticationService.authenticate(user.getEmail(), PASSWORD).getAccessToken();
    }

    private static RequestSpecification authenticated(String token) {
        return given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token);
    }
}
