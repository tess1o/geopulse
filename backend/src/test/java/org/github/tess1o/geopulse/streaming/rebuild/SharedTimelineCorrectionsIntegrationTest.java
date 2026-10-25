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

    private static io.restassured.specification.RequestSpecification authenticated(TestActor actor) {
        return given().contentType(ContentType.JSON).header("Authorization", "Bearer " + actor.accessToken());
    }

    private static Instant at(String time) {
        return CommuteWithStops.at(time);
    }
}
