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
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapStayOverrideRepository;
import org.github.tess1o.geopulse.streaming.service.DataGapStayOverrideService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestActor;
import org.github.tess1o.geopulse.testsupport.TestUserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two data gaps turned into stays at the place of the stay between them both join that stay. The stay then carries
 * two corrections, and the timeline must show both, so each can be undone.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class StayWithSeveralCorrectionsIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    TestUserFactory testUserFactory;

    @Inject
    TimelineDataGapRepository dataGapRepository;

    @Inject
    DataGapStayOverrideService dataGapStayOverrideService;

    @Inject
    TimelineDataGapStayOverrideRepository overrideRepository;

    private final CommuteWithStops day = new CommuteWithStops();

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void aStayJoinedByTwoGapConversionsShowsBoth() {
        TestActor actor = testUserFactory.createAuthenticatedUser("stay-two-gaps");
        UUID userId = day.ingest(harness, actor.userId());
        // At the office all day; the phone is off from noon to four and from five to nine.
        harness.ingest(userId, day.track
                .stay(day.office, at("16:00"), at("17:00"), 10)
                .stay(day.office, at("21:00"), at("22:00"), 10)
                .messages());
        harness.fullRebuild(userId);
        Long first = convertGapEndingAt(userId, at("16:00"));
        Long second = convertGapEndingAt(userId, at("21:00"));
        assertThat(QuarkusTransaction.requiringNew().call(() -> overrideRepository.findByUserId(userId).stream()
                .map(override -> override.getStay().getId())
                .distinct()
                .toList()))
                .as("the stay both conversions joined").hasSize(1);

        Response timeline = given().contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + actor.accessToken())
                .when().get("/api/v1/timeline?from=" + at("00:00") + "&to=" + at("23:59"));

        assertThat(timeline.statusCode()).isEqualTo(200);
        List<Number> overrideIds = timeline.jsonPath().getList("stays.find { it.timestamp == '" + at("08:46")
                + "' }.dataGapOverrideIds");
        assertThat(overrideIds).as("corrections shown on the office stay, oldest first")
                .extracting(Number::longValue)
                .containsExactly(first, second);
    }

    private Long convertGapEndingAt(UUID userId, Instant end) {
        TimelineDataGapEntity gap = QuarkusTransaction.requiringNew().call(() ->
                dataGapRepository.findByUserId(userId).stream()
                        .filter(candidate -> candidate.getEndTime().equals(end))
                        .findFirst()
                        .orElseThrow());
        DataGapStayOverrideRequest atTheOffice = new DataGapStayOverrideRequest();
        atTheOffice.setLocationStrategy("LATEST_POINT");
        return QuarkusTransaction.requiringNew().call(() ->
                dataGapStayOverrideService.convertGapToStay(userId, gap.getId(), atTheOffice).orElseThrow().overrideId());
    }

    private static Instant at(String time) {
        return CommuteWithStops.at(time);
    }
}
