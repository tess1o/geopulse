package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.repository.TimelineRebuildRequestRepository;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestActor;
import org.github.tess1o.geopulse.testsupport.TestUserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Undoing a correction regenerates the timeline from the corrected event to the present, which can take as long as a
 * full rebuild. Like any other change to the timeline (a GPS point edit), it must run in the background: the request
 * returns the job to follow, and a regeneration already running blocks the write until it has finished.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class UndoInBackgroundIntegrationTest {

    @Inject TimelineReplayHarness harness;
    @Inject ManualEditsSupport edits;
    @Inject TestUserFactory testUserFactory;
    @Inject TimelineRebuildRequestRepository rebuildRequestRepository;

    private final CommuteWithStops day = new CommuteWithStops();

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void undoingASplitReturnsTheJobThatRegeneratesTheTimeline() {
        TestActor actor = testUserFactory.createAuthenticatedUser("undo-split-job");
        UUID userId = day.ingest(harness, actor.userId());
        UUID split = edits.split(userId, at("08:16"), at("08:20"), day.bakery, "Bakery");
        // A full rebuild is pending, which a regeneration run inside the request would take on too.
        QuarkusTransaction.requiringNew().run(() ->
                rebuildRequestRepository.request(userId, StreamingTimelineGenerationService.DEFAULT_START_DATE));

        Response undo = authenticated(actor).when().post("/api/v1/timeline/corrections/" + split + "/undo");

        assertThat(undo.statusCode()).isEqualTo(200);
        String jobId = undo.jsonPath().getString("timelineJobId");
        assertThat(jobId).as("the job regenerating the timeline").isNotNull();
        assertThat(undo.jsonPath().getBoolean("timelineJobQueued")).as("the job started now").isFalse();
        edits.awaitJob(UUID.fromString(jobId));
        harness.fullRebuild(userId);
        assertThat(harness.snapshot(userId).stays()).noneSatisfy(stay -> assertThat(stay).contains("'Bakery'"));
        assertThat(harness.pendingRebuild(userId)).isEmpty();
    }

    private static RequestSpecification authenticated(TestActor actor) {
        return given().contentType(ContentType.JSON).header("Authorization", "Bearer " + actor.accessToken());
    }

    private static java.time.Instant at(String time) {
        return CommuteWithStops.at(time);
    }
}
