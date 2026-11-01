package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.streaming.model.dto.DataGapStayOverrideRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapStayOverrideRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripMovementOverrideRepository;
import org.github.tess1o.geopulse.streaming.service.DataGapStayOverrideService;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.TripMovementTypeOverrideService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.HibernateStatisticsTestProfile;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestActor;
import org.github.tess1o.geopulse.testsupport.TestUserFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A correction whose trip or data gap no longer comes out of generation (its points were deleted or changed) is kept,
 * in case the event returns. Such a correction must neither make every later regeneration read the user's whole
 * timeline to look for its event, nor stay out of the user's sight and reach.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@TestProfile(HibernateStatisticsTestProfile.class)
@SerializedDatabaseTest
class OrphanedOverridesIntegrationTest {

    private static final int DAYS = 20;
    private static final Instant FIRST_DAY = Instant.parse("2026-08-01T00:00:00Z");

    @Inject
    TimelineReplayHarness harness;

    @Inject
    TestUserFactory testUserFactory;

    @Inject
    TripMovementTypeOverrideService movementTypeOverrideService;

    @Inject
    TimelineTripMovementOverrideRepository movementOverrideRepository;

    @Inject
    DataGapStayOverrideService dataGapStayOverrideService;

    @Inject
    TimelineDataGapStayOverrideRepository dataGapOverrideRepository;

    @Inject
    TimelineDataGapRepository dataGapRepository;

    @Inject
    GpsPointService gpsPointService;

    @Inject
    StreamingTimelineGenerationService timelineGenerationService;

    @Inject
    ManualEditsSupport edits;

    @Inject
    EntityManager entityManager;

    private final Track track = new Track();
    private final double[] home = track.place(49.8420, 24.0310);
    private final double[] office = track.place(49.8350, 24.0750);

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void anOrphanedMovementTypeDoesNotMakeEveryRegenerationReadAllTrips() {
        UUID userId = commutes(testUserFactory.createUser("orphan-movement").userId());
        TimelineTripEntity firstCommute = edits.tripCovering(userId, day(0, "08:05"), day(0, "08:10"));
        movementTypeOverrideService.setManualMovementType(userId, firstCommute.getId(), TripType.TRAIN);
        orphanFirstCommute(userId);
        assertThat(QuarkusTransaction.requiringNew().call(() ->
                movementOverrideRepository.findUnmatchedByUserId(userId))).hasSize(1);

        long tripsLoaded = entitiesLoadedBy(TimelineTripEntity.class, () -> nextDayLive(userId));

        assertThat(tripsLoaded)
                .as("trips loaded by one real-time pass with a single orphaned movement type, out of %d", DAYS)
                .isLessThan(5);
    }

    @Test
    void anOrphanedGapStayDoesNotMakeEveryRegenerationReadAllDataGaps() {
        UUID userId = commutes(testUserFactory.createUser("orphan-gap").userId());
        orphanGapStayOnFirstNight(userId);

        long gapsLoaded = entitiesLoadedBy(TimelineDataGapEntity.class, () -> nextDayLive(userId));

        assertThat(gapsLoaded)
                .as("data gaps loaded by one real-time pass with a single orphaned gap stay, out of %d", DAYS)
                .isLessThan(5);
    }

    @Test
    void orphanedCorrectionsAreListedAndCanBeRemoved() {
        TestActor actor = testUserFactory.createAuthenticatedUser("orphan-list");
        UUID userId = commutes(actor.userId());
        TimelineTripEntity firstCommute = edits.tripCovering(userId, day(0, "08:05"), day(0, "08:10"));
        movementTypeOverrideService.setManualMovementType(userId, firstCommute.getId(), TripType.TRAIN);
        TimelineTripEntity secondCommute = edits.tripCovering(userId, day(1, "08:05"), day(1, "08:10"));
        movementTypeOverrideService.setManualMovementType(userId, secondCommute.getId(), TripType.BICYCLE);
        orphanFirstCommute(userId);

        Response listed = authenticated(actor).when().get("/api/v1/timeline/overrides");
        assertThat(listed.statusCode()).as("listing the corrections").isEqualTo(200);
        List<String> types = listed.jsonPath().getList("type");
        List<Boolean> applied = listed.jsonPath().getList("applied");
        List<String> movementTypes = listed.jsonPath().getList("movementType");
        assertThat(types).containsExactly("MOVEMENT_TYPE", "MOVEMENT_TYPE");
        assertThat(movementTypes).containsExactly("TRAIN", "BICYCLE");
        assertThat(applied).as("the first commute no longer exists").containsExactly(false, true);

        Number orphanId = listed.jsonPath().get("[0].id");
        assertThat(authenticated(actor).when()
                .delete("/api/v1/timeline/movement-overrides/" + orphanId.longValue())
                .statusCode()).as("removing the orphaned correction").isEqualTo(204);
        assertThat(authenticated(actor).when().get("/api/v1/timeline/overrides").jsonPath().getList("movementType"))
                .containsExactly("BICYCLE");
    }

    /** {@link #DAYS} days of home, a drive to the office, and the office. */
    private UUID commutes(UUID userId) {
        for (int i = 0; i < DAYS; i++) {
            track.stay(home, day(i, "06:00"), day(i, "08:00"), 10)
                    .drive(home, office, day(i, "08:01"), day(i, "08:20"))
                    .stay(office, day(i, "08:21"), day(i, "12:00"), 15);
        }
        harness.ingest(userId, track.messages());
        harness.fullRebuild(userId);
        return userId;
    }

    /** The first morning's points are deleted, so its commute no longer exists. */
    private void orphanFirstCommute(UUID userId) {
        List<Long> morning = harness.pointIds(userId, day(0, "00:00"), day(0, "23:59"));
        QuarkusTransaction.requiringNew().run(() -> gpsPointService.deleteGpsPoints(morning, userId));
        timelineGenerationService.generateTimelineFromTimestamp(userId, day(0, "00:00"));
    }

    /** The first night is turned into a stay, then the phone's points for it turn up, so the gap is gone. */
    private void orphanGapStayOnFirstNight(UUID userId) {
        TimelineDataGapEntity night = QuarkusTransaction.requiringNew().call(() ->
                dataGapRepository.findByUserId(userId).getFirst());
        DataGapStayOverrideRequest atHome = new DataGapStayOverrideRequest();
        atHome.setLocationStrategy("LATEST_POINT");
        QuarkusTransaction.requiringNew().run(() ->
                dataGapStayOverrideService.convertGapToStay(userId, night.getId(), atHome));
        harness.ingest(userId, new Track().stay(office, night.getStartTime().plusSeconds(600),
                night.getEndTime().minusSeconds(600), 60).messages());
        timelineGenerationService.generateTimelineFromTimestamp(userId, night.getStartTime());
        assertThat(QuarkusTransaction.requiringNew().call(() -> dataGapOverrideRepository.findByUserId(userId)))
                .singleElement()
                .satisfies(override -> assertThat(override.getStay()).as("the gap stay was not re-applied").isNull());
    }

    /** The next morning arrives live and the real-time job runs. */
    private void nextDayLive(UUID userId) {
        harness.ingest(userId, new Track().stay(home, day(DAYS, "06:00"), day(DAYS, "07:00"), 10).messages());
        harness.process(userId);
    }

    private long entitiesLoadedBy(Class<?> entity, Runnable work) {
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        work.run();
        return statistics.getEntityStatistics(entity.getName()).getLoadCount();
    }

    private static io.restassured.specification.RequestSpecification authenticated(TestActor actor) {
        return given().contentType(ContentType.JSON).header("Authorization", "Bearer " + actor.accessToken());
    }

    private static Instant day(int index, String time) {
        return FIRST_DAY.plus(Duration.ofDays(index)).plus(Duration.between(Instant.parse("1970-01-01T00:00:00Z"),
                Instant.parse("1970-01-01T" + time + ":00Z")));
    }
}
