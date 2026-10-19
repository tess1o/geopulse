package org.github.tess1o.geopulse.streaming.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.testsupport.TimelineTestFixtures;
import org.github.tess1o.geopulse.user.model.TimelinePreferences;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;

/**
 * {@code GET /api/v1/timeline/{stays|trips|data-gaps}/{id}/explanation}: a generated day at home, a drive to the
 * office, the office, and an afternoon with only inaccurate fixes that becomes a data gap before the evening at home.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineEventExplanationIntegrationTest {

    private static final String TIMELINE = "/api/v1/timeline";
    private static final String PASSWORD = "password123";
    private static final double HOME_LAT = 40.7589;
    private static final double HOME_LON = -73.9851;
    private static final double OFFICE_LAT = 40.7505;
    private static final double OFFICE_LON = -73.9934;
    private static final double CAFE_LAT = 40.7550;
    private static final double CAFE_LON = -73.9800;
    private static final Instant DAY = Instant.parse("2024-08-15T08:00:00Z");
    private static final double THRESHOLD = 60.0;
    private static final String SOURCE_TOKEN = "explanation-test-source-token";

    @Inject UserService userService;
    @Inject AuthenticationService authenticationService;
    @Inject GpsPointRepository gpsPointRepository;
    @Inject StreamingTimelineGenerationService timelineGenerationService;
    @Inject EntityManager entityManager;

    private TimelineTestFixtures fixtures;
    private UserEntity owner;
    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
        fixtures = TimelineTestFixtures.newScope();
        owner = register("explain-owner");
        ownerToken = token(owner);
        otherToken = token(register("explain-other"));
        seedDay();
    }

    @Test
    void stayShowsTheLeftOutFixesThePointThatEndedItAndTheSourceFilters() {
        TimelineStayEntity home = stays().getFirst();

        Response response = authenticated(ownerToken).when().get(TIMELINE + "/stays/" + home.getId() + "/explanation");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonPath json = response.jsonPath();

        assertThat(json.getString("kind")).isEqualTo("STAY");
        assertThat(json.getString("explanation.end.code")).isEqualTo("MOVED_AWAY");
        assertThat(json.getInt("radiusMeters")).isEqualTo(json.getInt("settings.staypointRadiusMeters"));
        assertThat(json.getDouble("settings.staypointMaxAccuracyThreshold")).isEqualTo(THRESHOLD);

        // The three inaccurate fixes 300 m away were left out, so they did not end the stay
        List<Map<String, Object>> leftOut = points(json, point -> "EVENT".equals(point.get("part"))
                && Boolean.FALSE.equals(point.get("used")));
        assertThat(leftOut).hasSize(3);
        assertThat(leftOut).allSatisfy(point -> assertThat(((Number) point.get("accuracyMeters")).doubleValue())
                .isEqualTo(200.0));
        assertThat(json.getInt("filtered.inEvent")).isEqualTo(3);
        assertThat(json.getDouble("filtered.thresholdMeters")).isEqualTo(THRESHOLD);

        // The point that ended it is the first point of the drive, outside the radius
        TimelineTripEntity drive = trips().getFirst();
        Map<String, Object> movedAway = onlyPointWith(json, "MOVED_AWAY");
        assertThat(movedAway.get("part")).isEqualTo("AFTER");
        assertThat(Instant.parse((String) movedAway.get("timestamp"))).isEqualTo(drive.getTimestamp());
        assertThat(movedAway.get("outsideRadius")).isEqualTo(true);

        // Confirmed once it had lasted the minimum stay duration
        Map<String, Object> confirmed = onlyPointWith(json, "CONFIRMED");
        assertThat(Instant.parse((String) confirmed.get("timestamp")))
                .isAfterOrEqualTo(home.getTimestamp().plus(Duration.ofMinutes(json.getInt("settings.staypointMinDurationMinutes"))));

        // The centre is the average of the used points, so it sits on the home fixes
        assertThat(Math.abs(json.getDouble("centre.latitude") - json.getDouble("location.latitude"))).isLessThan(1e-4);

        // The GPS source drops points before they are stored, and the dialog can say so, without its credentials
        assertThat(json.getList("ingestionFilters")).hasSize(1);
        assertThat(json.getInt("ingestionFilters[0].maxAccuracyMeters")).isEqualTo(100);
        assertThat(json.getInt("ingestionFilters[0].maxSpeedKmh")).isEqualTo(250);
        assertThat(json.getString("ingestionFilters[0].sourceType")).isEqualTo("OVERLAND");
        assertThat(response.asString()).doesNotContain(SOURCE_TOKEN);
    }

    @Test
    void tripStartsWithThePointThatLeftTheStayAndEndsWithTheStop() {
        TimelineTripEntity drive = trips().getFirst();

        JsonPath json = ok(TIMELINE + "/trips/" + drive.getId() + "/explanation");

        assertThat(json.getString("kind")).isEqualTo("TRIP");
        assertThat(json.getString("explanation.start.code")).isEqualTo("LEFT_STAY");
        Map<String, Object> left = onlyPointWith(json, "LEFT_STAY");
        assertThat(Instant.parse((String) left.get("timestamp"))).isEqualTo(drive.getTimestamp());
        assertThat(json.getMap("startPoint")).containsKeys("latitude", "longitude");

        assertThat(json.getString("explanation.end.code")).isEqualTo("ARRIVAL");
        List<Map<String, Object>> arrival = points(json, point -> reasons(point).contains("ARRIVAL"));
        assertThat(arrival).hasSize(json.getInt("explanation.end.params.points"));
        assertThat(Instant.parse((String) arrival.getFirst().get("timestamp")))
                .isEqualTo(drive.getTimestamp().plusSeconds(drive.getTripDuration()));
        assertThat(arrival.subList(1, arrival.size())).allSatisfy(point -> assertThat(point.get("part")).isEqualTo("AFTER"));
    }

    @Test
    void anAfternoonOfOnlyInaccurateFixesIsADataGapWithItsPointsLeftOut() {
        TimelineDataGapEntity gap = dataGaps().stream()
                .filter(candidate -> candidate.getStartTime().isBefore(DAY.plus(Duration.ofHours(8))))
                .findFirst().orElseThrow();

        JsonPath json = ok(TIMELINE + "/data-gaps/" + gap.getId() + "/explanation");

        assertThat(json.getString("kind")).isEqualTo("DATA_GAP");
        assertThat(json.getString("explanation.start.code")).isEqualTo("NO_POINTS");
        List<Map<String, Object>> inside = points(json, point -> "EVENT".equals(point.get("part")));
        assertThat(inside).isNotEmpty().allSatisfy(point -> assertThat(point.get("used")).isEqualTo(false));
        assertThat(json.getInt("filtered.inEvent")).isEqualTo(inside.size());

        List<Map<String, Object>> edges = points(json, point -> reasons(point).contains("NO_POINTS"));
        assertThat(edges).extracting(point -> point.get("part")).containsExactly("BEFORE", "AFTER");
        assertThat(Instant.parse((String) edges.getFirst().get("timestamp"))).isEqualTo(gap.getStartTime());
        assertThat(Instant.parse((String) edges.getLast().get("timestamp"))).isEqualTo(gap.getEndTime());
    }

    @Test
    void eventsOfAnotherUserOrMissingOnesAreNotFound() {
        long stayId = stays().getFirst().getId();
        assertProblemEnvelope(authenticated(otherToken).when().get(TIMELINE + "/stays/" + stayId + "/explanation"),
                404, "STAY_NOT_FOUND");
        assertProblemEnvelope(authenticated(ownerToken).when().get(TIMELINE + "/trips/999999999/explanation"),
                404, "TRIP_NOT_FOUND");
        assertProblemEnvelope(authenticated(ownerToken).when().get(TIMELINE + "/data-gaps/999999999/explanation"),
                404, "DATA_GAP_NOT_FOUND");
    }

    private void seedDay() {
        QuarkusTransaction.requiringNew().run(() -> {
            UserEntity user = entityManager.find(UserEntity.class, owner.getId());
            user.setTimelinePreferences(TimelinePreferences.builder()
                    .useVelocityAccuracy(true)
                    .staypointMaxAccuracyThreshold(THRESHOLD)
                    .dataGapThresholdSeconds(3600)
                    .dataGapMinDurationSeconds(1800)
                    .gapStayInferenceEnabled(false)
                    .gapTripInferenceEnabled(false)
                    .build());

            GpsSourceConfigEntity source = GpsSourceConfigEntity.builder()
                    .user(user)
                    .sourceType(GpsSourceType.OVERLAND)
                    .token(SOURCE_TOKEN)
                    .active(true)
                    .filterInaccurateData(true)
                    .maxAllowedAccuracy(100)
                    .maxAllowedSpeed(250)
                    .build();
            entityManager.persist(source);

            List<GpsPointEntity> points = new ArrayList<>();
            points.addAll(fixtures.stationaryPoints(user, HOME_LAT, HOME_LON, DAY, DAY.plus(Duration.ofHours(2)), 5));
            // Three fixes far off with poor accuracy in the middle of the morning at home
            for (int minute : new int[]{32, 47, 62}) {
                points.add(fixtures.point(user, HOME_LAT + 0.0027, HOME_LON, DAY.plusSeconds(minute * 60L + 30), 0.0, 200.0));
            }
            points.addAll(fixtures.movingPoints(user, HOME_LAT, HOME_LON, OFFICE_LAT, OFFICE_LON,
                    DAY.plus(Duration.ofMinutes(121)), DAY.plus(Duration.ofMinutes(150))));
            points.addAll(fixtures.stationaryPoints(user, OFFICE_LAT, OFFICE_LON,
                    DAY.plus(Duration.ofMinutes(151)), DAY.plus(Duration.ofHours(7)), 10));
            // A café visit with nothing but very inaccurate fixes
            Instant cafe = DAY.plus(Duration.ofHours(8));
            for (int minute = 0; minute <= 60; minute += 5) {
                points.add(fixtures.point(user, CAFE_LAT, CAFE_LON, cafe.plusSeconds(minute * 60L), 0.0, 500.0));
            }
            points.addAll(fixtures.stationaryPoints(user, HOME_LAT, HOME_LON,
                    DAY.plus(Duration.ofHours(11)), DAY.plus(Duration.ofHours(12)), 5));
            TimelineTestFixtures.persist(gpsPointRepository, points);
            TimelineTestFixtures.regenerateTimeline(timelineGenerationService, owner.getId());
        });
    }

    private List<TimelineStayEntity> stays() {
        return QuarkusTransaction.requiringNew().call(() -> entityManager.createQuery(
                        "select s from TimelineStayEntity s where s.user.id = :userId order by s.timestamp",
                        TimelineStayEntity.class)
                .setParameter("userId", owner.getId()).getResultList());
    }

    private List<TimelineTripEntity> trips() {
        return QuarkusTransaction.requiringNew().call(() -> entityManager.createQuery(
                        "select t from TimelineTripEntity t where t.user.id = :userId order by t.timestamp",
                        TimelineTripEntity.class)
                .setParameter("userId", owner.getId()).getResultList());
    }

    private List<TimelineDataGapEntity> dataGaps() {
        return QuarkusTransaction.requiringNew().call(() -> entityManager.createQuery(
                        "select g from TimelineDataGapEntity g where g.user.id = :userId order by g.startTime",
                        TimelineDataGapEntity.class)
                .setParameter("userId", owner.getId()).getResultList());
    }

    private JsonPath ok(String path) {
        Response response = authenticated(ownerToken).when().get(path);
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
        return response.jsonPath();
    }

    private static List<Map<String, Object>> points(JsonPath json, java.util.function.Predicate<Map<String, Object>> filter) {
        List<Map<String, Object>> points = json.getList("points");
        return points.stream().filter(filter).toList();
    }

    private static Map<String, Object> onlyPointWith(JsonPath json, String reason) {
        List<Map<String, Object>> matching = points(json, point -> reasons(point).contains(reason));
        assertThat(matching).as("points with reason %s", reason).hasSize(1);
        return matching.getFirst();
    }

    @SuppressWarnings("unchecked")
    private static List<String> reasons(Map<String, Object> point) {
        Object reasons = point.get("reasons");
        return reasons instanceof List<?> list ? (List<String>) list : List.of();
    }

    private UserEntity register(String prefix) {
        return userService.registerUser(TestIds.uniqueEmail(prefix), PASSWORD, prefix + " User", "UTC");
    }

    private String token(UserEntity user) {
        return authenticationService.authenticate(user.getEmail(), PASSWORD).getAccessToken();
    }

    private static RequestSpecification authenticated(String token) {
        return given().contentType(ContentType.JSON).header("Authorization", "Bearer " + token);
    }
}
