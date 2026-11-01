package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.model.dto.DataGapStayOverrideRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.service.DataGapStayOverrideService;
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

/**
 * Corrections are the owner's to see and undo. A timeline shown to someone else, through a share link, shows the
 * corrected timeline but not the owner's corrections behind it.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class SharedTimelineCorrectionsIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    ManualEditsSupport edits;

    @Inject
    TestUserFactory testUserFactory;

    @Inject
    DataGapStayOverrideService dataGapStayOverrideService;

    @Inject
    TimelineDataGapRepository dataGapRepository;

    @Inject org.github.tess1o.geopulse.streaming.edits.TimelineEditService unifiedEdits;
    @Inject org.github.tess1o.geopulse.streaming.service.StreamingTimelineAggregator aggregator;

    private final CommuteWithStops day = new CommuteWithStops();

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void sharedTimelineShowsASplitStayWithoutTheOwnersCorrection() {
        TestActor owner = testUserFactory.createAuthenticatedUser("shared-corrections");
        UUID userId = day.ingest(harness, owner.userId());
        harness.ingest(userId, day.track.stay(day.office, at("16:00"), at("17:00"), 10).messages());
        harness.fullRebuild(userId);
        edits.split(userId, at("08:16"), at("08:20"), day.bakery, "Bakery");
        TimelineDataGapEntity gap = QuarkusTransaction.requiringNew().call(() ->
                dataGapRepository.findByUserId(userId).stream()
                        .filter(candidate -> candidate.getEndTime().equals(at("16:00")))
                        .findFirst()
                        .orElseThrow());
        DataGapStayOverrideRequest atTheOffice = new DataGapStayOverrideRequest();
        atTheOffice.setLocationStrategy("LATEST_POINT");
        QuarkusTransaction.requiringNew().run(() ->
                dataGapStayOverrideService.convertGapToStay(userId, gap.getId(), atTheOffice));

        assertThat(authenticated(owner).when().get("/api/v1/timeline?from=" + at("00:00") + "&to=" + at("23:59"))
                .jsonPath().getList("stays.findAll { it.locationName == 'Bakery' }.tripSplitOverrideId"))
                .as("the owner's own timeline").doesNotContainNull().hasSize(1);
        assertThat(authenticated(owner).when().get("/api/v1/timeline?from=" + at("00:00") + "&to=" + at("23:59"))
                .jsonPath().getList("stays.findAll { it.dataGapOverrideIds != null }.dataGapOverrideIds"))
                .as("the owner's gap conversion").isNotEmpty();

        Response link = authenticated(owner)
                .body(Map.of(
                        "name", "Commute",
                        "expires_at", Instant.now().plusSeconds(7200).toString(),
                        "password", "secret",
                        "share_type", "TIMELINE",
                        "start_date", at("00:00").toString(),
                        "end_date", at("23:59").toString(),
                        "show_current_location", false,
                        "show_notes", false))
                .when().post("/api/v1/share-links");
        assertThat(link.statusCode()).isEqualTo(201);
        String linkId = link.jsonPath().getString("id");
        String guestToken = given().contentType(ContentType.JSON)
                .body(Map.of("password", "secret"))
                .when().post("/api/v1/public/share-links/" + linkId + "/access-tokens")
                .jsonPath().getString("access_token");

        Response shared = given().header("Authorization", "Bearer " + guestToken)
                .when().get("/api/v1/public/share-links/" + linkId + "/timeline");

        assertThat(shared.statusCode()).isEqualTo(200);
        List<Map<String, Object>> stays = shared.jsonPath().getList("stays");
        assertThat(stays).anySatisfy(stay -> assertThat(stay.get("locationName")).isEqualTo("Bakery"));
        assertThat(stays).as("stays seen through the share link").allSatisfy(stay -> {
            assertThat(stay.get("tripSplitOverrideId")).isNull();
            assertThat(stay.get("dataGapOverrideIds")).isNull();
        });
    }

    @Test
    void unifiedEditsKeepSharedContentButHideOwnerHistory() {
        TestActor owner = testUserFactory.createAuthenticatedUser("unified-shared-owner");
        UUID user = day.ingest(harness, owner.userId());
        var split = new org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest();
        split.setKind(org.github.tess1o.geopulse.streaming.edits.TimelineEditKind.SPLIT_STAY);
        split.setAnchorStart(at("08:46")); split.setAnchorEnd(at("12:00"));
        split.setPayload(new java.util.LinkedHashMap<>(Map.of("splitAt", at("10:00").toString(),
                "rightLocationName", "Shared destination", "rightLatitude", 50.0, "rightLongitude", 30.0)));
        unifiedEdits.create(user, split); harness.fullRebuild(user);
        var label = new org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest();
        label.setKind(org.github.tess1o.geopulse.streaming.edits.TimelineEditKind.SET_VISIT_NAME);
        label.setAnchorStart(at("10:00")); label.setAnchorEnd(at("12:00"));
        label.setPayload(new java.util.LinkedHashMap<>(Map.of("visitName", "Shared visit label")));
        var saved = unifiedEdits.create(user, label); harness.fullRebuild(user);
        assertThat(aggregator.getTimelineFromDb(user, at("00:00"), at("23:59")).getStays())
                .anySatisfy(stay -> assertThat(stay.getActiveEditCount()).isPositive());
        var viewer = aggregator.getTimelineFromDbForViewer(user, at("00:00"), at("23:59"), false);
        assertThat(viewer.getStays()).anySatisfy(stay -> {
            assertThat(stay.getLocationName()).isEqualTo("Shared destination");
            assertThat(stay.getVisitName()).isEqualTo("Shared visit label");
        }).allSatisfy(stay -> {
            assertThat(stay.getActiveEditCount()).isZero();
            assertThat(stay.getTripSplitOverrideId()).isNull();
            assertThat(stay.getDataGapOverrideIds()).isNull();
        });
        var link = authenticated(owner).body(Map.of("name", "Unified share", "share_type", "TIMELINE",
                "expires_at", Instant.now().plusSeconds(7200).toString(), "start_date", at("00:00").toString(),
                "end_date", at("23:59").toString(), "show_current_location", false, "show_notes", false))
                .post("/api/v1/share-links");
        assertThat(link.statusCode()).isEqualTo(201);
        String linkId = link.jsonPath().getString("id");
        String guest = given().contentType(ContentType.JSON).body("{}")
                .post("/api/v1/public/share-links/" + linkId + "/access-tokens").jsonPath().getString("access_token");
        assertThat(guest).isNotBlank();
        var shared = given().header("Authorization", "Bearer " + guest).get("/api/v1/public/share-links/" + linkId + "/timeline");
        assertThat(shared.statusCode()).isEqualTo(200);
        assertThat(shared.jsonPath().getList("stays.activeEditCount", Integer.class)).allMatch(count -> count == 0);
        assertThat(shared.asString()).contains("Shared destination", "Shared visit label")
                .doesNotContain("beforeValues", "supersededBy", "editApplications");
        var other = testUserFactory.createAuthenticatedUser("unified-shared-viewer");
        assertThat(authenticated(other).get("/api/v1/timeline/edits").jsonPath().getList("$")).isEmpty();
        assertThat(authenticated(other).post("/api/v1/timeline/edits/" + saved.id() + "/revert").statusCode()).isEqualTo(404);
        assertThat(unifiedEdits.list(user)).allSatisfy(edit -> assertThat(edit.applied()).isTrue());
    }

    private static io.restassured.specification.RequestSpecification authenticated(TestActor actor) {
        return given().contentType(ContentType.JSON).header("Authorization", "Bearer " + actor.accessToken());
    }

    private static Instant at(String time) {
        return CommuteWithStops.at(time);
    }
}
