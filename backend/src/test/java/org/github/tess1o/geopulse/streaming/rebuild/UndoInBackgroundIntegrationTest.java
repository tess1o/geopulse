package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.model.dto.DataGapStayOverrideRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineRebuildRequestRepository;
import org.github.tess1o.geopulse.streaming.service.DataGapStayOverrideService;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestActor;
import org.github.tess1o.geopulse.testsupport.TestUserFactory;
import org.github.tess1o.geopulse.user.model.TimelineStatus;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Undoing a correction regenerates the timeline from the corrected event to the present, which can take as long as a
 * full rebuild. Like any other change to the timeline (a GPS point edit), it must run in the background: the request
 * returns the job to follow, and a regeneration already running only delays it.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class UndoInBackgroundIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    ManualEditsSupport edits;

    @Inject
    TestUserFactory testUserFactory;

    @Inject
    UserRepository userRepository;

    @Inject
    TimelineRebuildRequestRepository rebuildRequestRepository;

    @Inject
    DataGapStayOverrideService dataGapStayOverrideService;

    @Inject
    TimelineDataGapRepository dataGapRepository;

    private final CommuteWithStops day = new CommuteWithStops();

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void undoingASplitReturnsTheJobThatRegeneratesTheTimeline() {
        TestActor actor = testUserFactory.createAuthenticatedUser("undo-split-job");
        UUID userId = day.ingest(harness, actor.userId());
        Long split = edits.split(userId, at("08:16"), at("08:20"), day.bakery, "Bakery");
        // A full rebuild is pending, which a regeneration run inside the request would take on too.
        QuarkusTransaction.requiringNew().run(() ->
                rebuildRequestRepository.request(userId, StreamingTimelineGenerationService.DEFAULT_START_DATE));

        Response undo = authenticated(actor).when().delete("/api/v1/timeline/stay-split-overrides/" + split);

        assertThat(undo.statusCode()).isEqualTo(200);
        String jobId = undo.jsonPath().getString("timelineJobId");
        assertThat(jobId).as("the job regenerating the timeline").isNotNull();
        assertThat(undo.jsonPath().getBoolean("timelineJobQueued")).as("the job started now").isFalse();
        edits.awaitJob(UUID.fromString(jobId));
        assertThat(harness.snapshot(userId).stays()).noneSatisfy(stay -> assertThat(stay).contains("'Bakery'"));
        assertThat(harness.pendingRebuild(userId)).isEmpty();
    }

    @Test
    void undoingASplitWhileTheTimelineIsBeingRegeneratedIsAppliedAfterIt() {
        TestActor actor = testUserFactory.createAuthenticatedUser("undo-split-busy");
        UUID userId = day.ingest(harness, actor.userId());
        Long split = edits.split(userId, at("08:16"), at("08:20"), day.bakery, "Bakery");
        setTimelineStatus(userId, TimelineStatus.PROCESSING);

        Response undo = authenticated(actor).when().delete("/api/v1/timeline/stay-split-overrides/" + split);

        assertThat(undo.statusCode()).as("undo while a regeneration runs").isEqualTo(200);
        assertThat(undo.jsonPath().getString("timelineJobId")).as("no job could start now").isNull();
        assertThat(undo.jsonPath().getBoolean("timelineJobQueued")).as("the regeneration is queued").isTrue();
        assertThat(harness.pendingRebuild(userId)).as("the regeneration still owed").isPresent();
        setTimelineStatus(userId, TimelineStatus.IDLE);
        edits.awaitTimelineJobs(userId);
        harness.process(userId);
        assertThat(harness.snapshot(userId).stays()).noneSatisfy(stay -> assertThat(stay).contains("'Bakery'"));
    }

    @Test
    void undoingAGapConversionWhileTheTimelineIsBeingRegeneratedIsAppliedAfterIt() {
        TestActor actor = testUserFactory.createAuthenticatedUser("undo-gap-busy");
        UUID userId = day.ingest(harness, actor.userId());
        harness.ingest(userId, day.track.stay(day.office, at("16:00"), at("17:00"), 10).messages());
        harness.fullRebuild(userId);
        TimelineDataGapEntity gap = QuarkusTransaction.requiringNew().call(() ->
                dataGapRepository.findByUserId(userId).stream()
                        .filter(candidate -> candidate.getEndTime().equals(at("16:00")))
                        .findFirst()
                        .orElseThrow());
        DataGapStayOverrideRequest atTheOffice = new DataGapStayOverrideRequest();
        atTheOffice.setLocationStrategy("LATEST_POINT");
        Long conversion = QuarkusTransaction.requiringNew().call(() ->
                dataGapStayOverrideService.convertGapToStay(userId, gap.getId(), atTheOffice).orElseThrow().overrideId());
        setTimelineStatus(userId, TimelineStatus.PROCESSING);

        Response undo = authenticated(actor).when()
                .delete("/api/v1/timeline/data-gap-overrides/" + conversion + "/stay-conversion");

        assertThat(undo.statusCode()).as("undo while a regeneration runs").isEqualTo(200);
        assertThat(undo.jsonPath().getString("timelineJobId")).as("no job could start now").isNull();
        assertThat(undo.jsonPath().getBoolean("timelineJobQueued")).as("the regeneration is queued").isTrue();
        assertThat(harness.pendingRebuild(userId)).as("the regeneration still owed").isPresent();
        setTimelineStatus(userId, TimelineStatus.IDLE);
        edits.awaitTimelineJobs(userId);
        harness.process(userId);
        assertThat(harness.snapshot(userId).gaps()).anySatisfy(item -> assertThat(item)
                .startsWith("GAP " + gap.getStartTime() + " "));
    }

    private void setTimelineStatus(UUID userId, TimelineStatus status) {
        QuarkusTransaction.requiringNew().run(() -> userRepository.findById(userId).setTimelineStatus(status));
    }

    private static RequestSpecification authenticated(TestActor actor) {
        return given().contentType(ContentType.JSON).header("Authorization", "Bearer " + actor.accessToken());
    }

    private static Instant at(String time) {
        return CommuteWithStops.at(time);
    }
}
