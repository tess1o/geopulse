package org.github.tess1o.geopulse.statistics.resource;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.testsupport.TimelineTestFixtures;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;

/**
 * HTTP contract for {@code /api/v1/statistics}.
 *
 * <p>Note what is <em>not</em> asserted here: the DAYS/WEEKS grouping derivation
 * ({@code Duration.between(start, end).toDays() < 10}) is internal — {@code UserStatistics} does not
 * expose the chart group mode, so it can only be tested at the service/unit level, which
 * {@code StatisticsResourceTest} already does.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class StatisticsContractTest {

    private static final String STATISTICS = "/api/v1/statistics";
    private static final String PASSWORD = "password123";
    private static final double HOME_LAT = 40.7589;
    private static final double HOME_LON = -73.9851;
    private static final double OFFICE_LAT = 40.7505;
    private static final double OFFICE_LON = -73.9934;

    @Inject UserService userService;
    @Inject AuthenticationService authenticationService;
    @Inject GpsPointRepository gpsPointRepository;
    @Inject StreamingTimelineGenerationService timelineGenerationService;

    private UserEntity owner;
    private String ownerToken;
    private TimelineTestFixtures fixtures;

    @BeforeEach
    @Transactional
    void setUp() {
        fixtures = TimelineTestFixtures.newScope();
        owner = register("statistics-owner");
        ownerToken = token(owner);
    }

    @Test
    void everyStatisticsViewAnswersOnAnEmptyAccount() {
        Response range = authenticated(ownerToken).when().get(STATISTICS);
        assertThat(range.statusCode()).isEqualTo(200);
        assertThat(range.jsonPath().getDouble("totalDistanceMeters")).isZero();

        assertThat(authenticated(ownerToken).when().get(STATISTICS + "/weekly").statusCode()).isEqualTo(200);
        assertThat(authenticated(ownerToken).when().get(STATISTICS + "/monthly").statusCode()).isEqualTo(200);
    }

    @Test
    void rangeViewReflectsGeneratedTimelineData() {
        Instant dayStart = Instant.parse("2024-08-15T08:00:00Z");
        // Committed in its own transaction: the HTTP request below runs on another thread and would
        // not see data left uncommitted by the test method.
        QuarkusTransaction.requiringNew().run(() -> {
            TimelineTestFixtures.persist(gpsPointRepository,
                    fixtures.homeToOfficeDay(owner, HOME_LAT, HOME_LON, OFFICE_LAT, OFFICE_LON, dayStart));
            TimelineTestFixtures.regenerateTimeline(timelineGenerationService, owner.getId());
        });

        Response response = authenticated(ownerToken)
                .queryParam("from", "2024-08-15T00:00:00Z")
                .queryParam("to", "2024-08-16T00:00:00Z")
                .when().get(STATISTICS);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getDouble("totalDistanceMeters"))
                .as("the generated trip should contribute distance")
                .isPositive();
        assertThat(response.jsonPath().getLong("uniqueLocationsCount"))
                .as("home and office are two distinct locations")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void invalidRangesUseTheExactStatisticsCode() {
        assertProblemEnvelope(authenticated(ownerToken)
                        .queryParam("from", "2024-08-16T00:00:00Z")
                        .queryParam("to", "2024-08-15T00:00:00Z")
                        .when().get(STATISTICS),
                400, "INVALID_STATISTICS_RANGE");

        assertProblemEnvelope(authenticated(ownerToken)
                        .queryParam("from", "not-an-instant")
                        .when().get(STATISTICS),
                400, "INVALID_STATISTICS_RANGE");

        assertProblemEnvelope(authenticated(ownerToken)
                        .queryParam("to", "15/08/2024")
                        .when().get(STATISTICS),
                400, "INVALID_STATISTICS_RANGE");
    }

    @Test
    void rangedViewsAreAlsoServedForAnEmptyAccount() {
        // A window with no data at all: still a valid request, still 200 with zeroed figures.
        Response response = authenticated(ownerToken)
                .queryParam("from", "2000-01-01T00:00:00Z")
                .queryParam("to", "2000-01-02T00:00:00Z")
                .when().get(STATISTICS);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getLong("timeMoving")).isZero();
    }

    private UserEntity register(String prefix) {
        String email = TestIds.uniqueEmail(prefix);
        return userService.registerUser(email, PASSWORD, prefix + " User", "UTC");
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
