package org.github.tess1o.geopulse.streaming.rest;

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
import org.github.tess1o.geopulse.streaming.service.AsyncTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.TimelineJobProgressService;
import org.github.tess1o.geopulse.testsupport.AsyncTimelineTestMocks;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.testsupport.TimelineTestFixtures;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;

/**
 * HTTP contract for {@code /api/v1/timeline}.
 *
 * <p>Seed data is committed with {@link QuarkusTransaction#requiringNew()} because the HTTP request
 * runs on a different thread and cannot see an uncommitted test transaction. Timeline regeneration
 * is the <em>synchronous</em> {@code regenerateFullTimeline}; {@link AsyncTimelineTestMocks} only
 * replaces the async job entry point used by {@code POST /timeline/jobs}, so the two coexist.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class StreamingTimelineContractTest {

    private static final String TIMELINE = "/api/v1/timeline";
    private static final String PASSWORD = "password123";
    private static final double HOME_LAT = 40.7589;
    private static final double HOME_LON = -73.9851;
    private static final double OFFICE_LAT = 40.7505;
    private static final double OFFICE_LON = -73.9934;
    private static final Instant DAY_START = Instant.parse("2024-08-15T08:00:00Z");
    private static final String DAY_FROM = "2024-08-15T00:00:00Z";
    private static final String DAY_TO = "2024-08-16T00:00:00Z";

    @Inject UserService userService;
    @Inject AuthenticationService authenticationService;
    @Inject GpsPointRepository gpsPointRepository;
    @Inject StreamingTimelineGenerationService timelineGenerationService;
    @Inject TimelineJobProgressService timelineJobProgressService;

    private UserEntity owner;
    private UserEntity other;
    private String ownerToken;
    private TimelineTestFixtures fixtures;

    @BeforeEach
    @Transactional
    void setUp() {
        GeocodingTestMocks.install();
        // Keeps POST /timeline/jobs deterministic — it must not launch a real rebuild.
        AsyncTimelineTestMocks.install();
        fixtures = TimelineTestFixtures.newScope();

        owner = register("timeline-owner");
        other = register("timeline-other");
        ownerToken = token(owner);
    }

    @Test
    void servesTimelineAndCountFromGeneratedData() {
        seedTimeline();

        Response timeline = authenticated(ownerToken)
                .queryParam("from", DAY_FROM).queryParam("to", DAY_TO)
                .when().get(TIMELINE);
        assertThat(timeline.statusCode()).isEqualTo(200);

        Response count = authenticated(ownerToken)
                .queryParam("from", DAY_FROM).queryParam("to", DAY_TO)
                .when().get(TIMELINE + "/count");
        assertThat(count.statusCode()).isEqualTo(200);
        assertThat(count.jsonPath().getLong("stays")).isGreaterThanOrEqualTo(2);
        assertThat(count.jsonPath().getLong("trips")).isGreaterThanOrEqualTo(1);
        assertThat(count.jsonPath().getLong("totalItems")).isPositive();
        assertThat(count.jsonPath().getLong("limit")).isPositive();

        assertThat(authenticated(ownerToken).when().get(TIMELINE + "/preferences").statusCode()).isEqualTo(200);
    }

    @Test
    void locationLookupRequiresBothCoordinates() {
        assertProblemEnvelope(authenticated(ownerToken).when().get(TIMELINE + "/location-lookup"),
                400, "INVALID_LOCATION_LOOKUP");
        assertProblemEnvelope(authenticated(ownerToken)
                        .queryParam("latitude", HOME_LAT)
                        .when().get(TIMELINE + "/location-lookup"),
                400, "INVALID_LOCATION_LOOKUP");

        seedTimeline();
        Response lookup = authenticated(ownerToken)
                .queryParam("latitude", OFFICE_LAT)
                .queryParam("longitude", OFFICE_LON)
                .when().get(TIMELINE + "/location-lookup");
        assertThat(lookup.statusCode()).isEqualTo(200);
    }

    @Test
    void timelineJobEndpointsAreServed() {
        // No job running on a fresh account.
        assertThat(authenticated(ownerToken).when().get(TIMELINE + "/jobs/current").statusCode()).isEqualTo(204);

        Response history = authenticated(ownerToken).when().get(TIMELINE + "/jobs/history");
        assertThat(history.statusCode()).isEqualTo(200);
        assertThat(history.jsonPath().getList("$")).isEmpty();

        Response started = authenticated(ownerToken).contentType(ContentType.JSON)
                .when().post(TIMELINE + "/jobs");
        assertThat(started.statusCode()).isEqualTo(200);
        assertThat(UUID.fromString(started.jsonPath().getString("jobId"))).isNotNull();
    }

    @Test
    void jobProgressFailuresUseExactCodes() {
        assertProblemEnvelope(authenticated(ownerToken).when().get(TIMELINE + "/jobs/not-a-uuid"),
                400, "INVALID_TIMELINE_JOB_ID");
        assertProblemEnvelope(authenticated(ownerToken).when().get(TIMELINE + "/jobs/" + UUID.randomUUID()),
                404, "TIMELINE_JOB_NOT_FOUND");

        // A job owned by someone else must not be readable.
        UUID otherJobId = timelineJobProgressService.createJob(other.getId());
        try {
            assertProblemEnvelope(authenticated(ownerToken).when().get(TIMELINE + "/jobs/" + otherJobId),
                    403, "TIMELINE_JOB_ACCESS_DENIED");
        } finally {
            timelineJobProgressService.completeJob(otherJobId);
        }
    }

    @Test
    void invalidRequestsUseTheExactTimelineCode() {
        assertProblemEnvelope(authenticated(ownerToken)
                        .queryParam("from", DAY_TO).queryParam("to", DAY_FROM)
                        .when().get(TIMELINE),
                400, "INVALID_TIMELINE_REQUEST");
        assertProblemEnvelope(authenticated(ownerToken).queryParam("from", "yesterday").when().get(TIMELINE),
                400, "INVALID_TIMELINE_REQUEST");
        assertProblemEnvelope(authenticated(ownerToken)
                        .queryParam("from", DAY_TO).queryParam("to", DAY_FROM)
                        .when().get(TIMELINE + "/count"),
                400, "INVALID_TIMELINE_REQUEST");
        assertProblemEnvelope(authenticated(ownerToken)
                        .queryParam("userIds", "not-a-uuid")
                        .when().get(TIMELINE + "/multi-user"),
                400, "INVALID_TIMELINE_REQUEST");
    }

    @Test
    void multiUserTimelineRequiresAFriendship() {
        Response response = authenticated(ownerToken)
                .queryParam("userIds", other.getId().toString())
                .when().get(TIMELINE + "/multi-user");
        assertProblemEnvelope(response, 403, "ACCESS_DENIED");
    }

    @Test
    void dataGapAndSplitOverrideEndpointsReportMissingRecords() {
        assertProblemEnvelope(authenticated(ownerToken)
                        .when().delete(TIMELINE + "/stay-split-overrides/999999"),
                404, "TRIP_SPLIT_OVERRIDE_NOT_FOUND");
        assertProblemEnvelope(authenticated(ownerToken)
                        .when().get(TIMELINE + "/data-gaps/999999/stay-conversion-preview"),
                404, "DATA_GAP_NOT_FOUND");
        assertProblemEnvelope(authenticated(ownerToken).contentType(ContentType.JSON)
                        .body("{}")
                        .when().put(TIMELINE + "/data-gaps/999999/stay-conversion"),
                404, "DATA_GAP_NOT_FOUND");
        assertProblemEnvelope(authenticated(ownerToken)
                        .when().delete(TIMELINE + "/data-gap-overrides/999999/stay-conversion"),
                404, "DATA_GAP_OVERRIDE_NOT_FOUND");
    }

    private void seedTimeline() {
        QuarkusTransaction.requiringNew().run(() -> {
            TimelineTestFixtures.persist(gpsPointRepository,
                    fixtures.homeToOfficeDay(owner, HOME_LAT, HOME_LON, OFFICE_LAT, OFFICE_LON, DAY_START));
            TimelineTestFixtures.regenerateTimeline(timelineGenerationService, owner.getId());
        });
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
