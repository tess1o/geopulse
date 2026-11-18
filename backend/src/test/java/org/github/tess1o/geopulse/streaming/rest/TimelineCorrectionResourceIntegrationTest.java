package org.github.tess1o.geopulse.streaming.rest;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.rebuild.CommuteWithStops;
import org.github.tess1o.geopulse.streaming.rebuild.ManualEditsSupport;
import org.github.tess1o.geopulse.streaming.rebuild.TimelineReplayHarness;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestActor;
import org.github.tess1o.geopulse.testsupport.TestUserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/** The unified correction API: preview, save, list, filter, undo and ownership. */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineCorrectionResourceIntegrationTest {

    @Inject TimelineReplayHarness harness;
    @Inject ManualEditsSupport edits;
    @Inject TestUserFactory users;

    private final CommuteWithStops day = new CommuteWithStops();

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void previewSaveListFilterUndoAndOwnership() {
        TestActor actor = users.createAuthenticatedUser("correction-http");
        day.ingest(harness, actor.userId());
        var trip = edits.tripCovering(actor.userId(), at("08:16"), at("08:20"));
        Map<String, Object> activity = Map.of(
                "segmentType", "STAY",
                "startTime", at("08:16").toString(),
                "endTime", at("08:20").toString(),
                "latitude", 49.8385,
                "longitude", 24.0530,
                "locationName", "Bakery");
        Map<String, Object> request = Map.of("activities", List.of(activity));

        Response preview = authenticated(actor).body(request)
                .post("/api/v1/timeline/corrections/preview");
        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(preview.jsonPath().getString("token")).isNotBlank();
        assertThat(preview.jsonPath().getList("events")).isNotEmpty();
        assertThat(preview.jsonPath().getList("warnings.code")).contains("CARVES_EVENT");

        Map<String, Object> saveRequest = new java.util.LinkedHashMap<>(request);
        saveRequest.put("previewToken", preview.jsonPath().getString("token"));
        Response saved = authenticated(actor).body(saveRequest).post("/api/v1/timeline/corrections");
        assertThat(saved.statusCode()).isEqualTo(200);
        String groupId = saved.jsonPath().getString("groupId");
        assertThat(groupId).isNotBlank();
        String jobId = saved.jsonPath().getString("timelineJobId");
        if (jobId != null) {
            edits.awaitJob(UUID.fromString(jobId));
        }

        Response listed = authenticated(actor)
                .queryParam("from", at("00:00").toString())
                .queryParam("to", at("23:59").toString())
                .get("/api/v1/timeline/corrections");
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(listed.jsonPath().getList("op")).containsExactly("PLACE_STAY");
        assertThat(listed.jsonPath().getList("status")).containsExactly("IN_EFFECT");
        assertThat(listed.jsonPath().getString("[0].groupId")).isEqualTo(groupId);

        var other = users.createAuthenticatedUser("correction-http-other");
        assertThat(authenticated(other).post("/api/v1/timeline/corrections/" + groupId + "/undo").statusCode())
                .isEqualTo(404);

        Response undone = authenticated(actor).post("/api/v1/timeline/corrections/" + groupId + "/undo");
        assertThat(undone.statusCode()).isEqualTo(200);
        assertThat(undone.jsonPath().getString("groupId")).isEqualTo(groupId);
        assertThat(undone.jsonPath().getList("edits.status")).containsExactly("UNDONE");
        assertThat(trip.getId()).isNotNull();
    }

    private static io.restassured.specification.RequestSpecification authenticated(TestActor actor) {
        return given().contentType(ContentType.JSON).header("Authorization", "Bearer " + actor.accessToken());
    }

    private static Instant at(String time) {
        return Instant.parse("2026-09-15T" + time + ":00Z");
    }
}
