package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.events.TimelineDataChangedEvent;
import org.github.tess1o.geopulse.streaming.model.dto.DataGapStayOverrideRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.service.DataGapStayOverrideService;
import org.github.tess1o.geopulse.streaming.service.TimelineUserLock;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestActor;
import org.github.tess1o.geopulse.testsupport.TestUserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A manual correction rewrites the stored timeline around it, as a regeneration does. While a regeneration of the
 * user's timeline runs (it holds the user's timeline lock), a correction must be refused with a conflict rather than
 * race with it; once made, it must be announced like a regeneration, so map matching, weather and visit matching see
 * the new stay and trips.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class ManualEditDuringRegenerationIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    ManualEditsSupport edits;

    @Inject
    TestUserFactory testUserFactory;

    @Inject
    TimelineUserLock userLock;

    @Inject
    TimelineDataGapRepository dataGapRepository;

    @Inject
    DataGapStayOverrideService dataGapStayOverrideService;

    @Inject
    TimelineChangeRecorder changes;

    private final CommuteWithStops day = new CommuteWithStops();

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void splittingATripWhileTheTimelineIsRegeneratedIsRefused() {
        TestActor actor = testUserFactory.createAuthenticatedUser("split-during-regeneration");
        UUID userId = day.ingest(harness, actor.userId());
        Long tripId = edits.tripCovering(userId, at("08:16"), at("08:20")).getId();

        Response split = whileTimelineLocked(userId, () -> authenticated(actor)
                .body(splitAtTheBakery())
                .when().put("/api/v1/trips/" + tripId + "/timeline/stay-split"));

        assertThat(split.statusCode()).as("split while a regeneration runs").isEqualTo(409);
        assertThat(harness.snapshot(userId).stays()).noneSatisfy(stay -> assertThat(stay).contains("'Bakery'"));
    }

    @Test
    void convertingAGapWhileTheTimelineIsRegeneratedIsRefused() {
        TestActor actor = testUserFactory.createAuthenticatedUser("gap-during-regeneration");
        UUID userId = afternoonAfterAGap(actor.userId());
        TimelineDataGapEntity gap = gapEndingAt(userId, at("16:00"));

        Response conversion = whileTimelineLocked(userId, () -> authenticated(actor)
                .body(Map.of("locationStrategy", "LATEST_POINT"))
                .when().put("/api/v1/timeline/data-gaps/" + gap.getId() + "/stay-conversion"));

        assertThat(conversion.statusCode()).as("conversion while a regeneration runs").isEqualTo(409);
        assertThat(harness.snapshot(userId).gaps())
                .anySatisfy(item -> assertThat(item).startsWith("GAP " + gap.getStartTime() + " "));
    }

    @Test
    void aSplitIsAnnouncedAsATimelineChange() {
        UUID userId = day.ingest(harness, "split-announced");
        int before = changes.eventsFor(userId).size();

        edits.split(userId, at("08:16"), at("08:20"), day.bakery, "Bakery");

        assertThat(changes.eventsFor(userId)).as("timeline changes announced by the split")
                .hasSize(before + 1)
                .last()
                .satisfies(event -> assertCovers(event, at("08:16"), at("08:20")));
    }

    @Test
    void aGapConversionIsAnnouncedAsATimelineChange() {
        UUID userId = afternoonAfterAGap(harness.createUser("gap-announced"));
        TimelineDataGapEntity gap = gapEndingAt(userId, at("16:00"));
        int before = changes.eventsFor(userId).size();

        DataGapStayOverrideRequest atTheOffice = new DataGapStayOverrideRequest();
        atTheOffice.setLocationStrategy("LATEST_POINT");
        QuarkusTransaction.requiringNew().run(() ->
                dataGapStayOverrideService.convertGapToStay(userId, gap.getId(), atTheOffice));

        assertThat(changes.eventsFor(userId)).as("timeline changes announced by the conversion")
                .hasSize(before + 1)
                .last()
                .satisfies(event -> assertCovers(event, gap.getStartTime(), gap.getEndTime()));
    }

    private static void assertCovers(TimelineDataChangedEvent event, Instant from, Instant to) {
        assertThat(event.getAffectedFrom()).isBeforeOrEqualTo(from);
        assertThat(event.getAffectedTo()).isAfterOrEqualTo(to);
    }

    /** Runs the request while another transaction holds the user's timeline lock, as a regeneration does. */
    private Response whileTimelineLocked(UUID userId, Supplier<Response> request) {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> QuarkusTransaction.requiringNew().run(() -> {
            assertThat(userLock.tryLock(userId)).isTrue();
            locked.countDown();
            try {
                done.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        try {
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            return request.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } finally {
            done.countDown();
            holder.join();
        }
    }

    private UUID afternoonAfterAGap(UUID userId) {
        day.ingest(harness, userId);
        harness.ingest(userId, day.track.stay(day.office, at("16:00"), at("17:00"), 10).messages());
        harness.fullRebuild(userId);
        return userId;
    }

    private TimelineDataGapEntity gapEndingAt(UUID userId, Instant end) {
        return QuarkusTransaction.requiringNew().call(() -> dataGapRepository.findByUserId(userId).stream()
                .filter(candidate -> candidate.getEndTime().equals(end))
                .findFirst()
                .orElseThrow());
    }

    private Map<String, Object> splitAtTheBakery() {
        return Map.of(
                "stayStartTime", at("08:16").toString(),
                "stayEndTime", at("08:20").toString(),
                "anchorTimestamp", at("08:18").toString(),
                "latitude", day.bakery[0],
                "longitude", day.bakery[1],
                "locationName", "Bakery");
    }

    private static RequestSpecification authenticated(TestActor actor) {
        return given().contentType(ContentType.JSON).header("Authorization", "Bearer " + actor.accessToken());
    }

    private static Instant at(String time) {
        return CommuteWithStops.at(time);
    }
}
