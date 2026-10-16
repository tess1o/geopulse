package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.service.RealtimeTimelineProcessor.Outcome;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.trips.model.dto.CreateTripDto;
import org.github.tess1o.geopulse.trips.model.dto.CreateTripPlanItemDto;
import org.github.tess1o.geopulse.trips.model.dto.TripPlanItemDto;
import org.github.tess1o.geopulse.trips.model.dto.UpdateTripDto;
import org.github.tess1o.geopulse.trips.model.dto.UpdateTripPlanItemDto;
import org.github.tess1o.geopulse.trips.service.TripPlanItemService;
import org.github.tess1o.geopulse.trips.service.TripService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Planned visits used to be matched only when the real-time job rebuilt the timeline every few minutes. Now that it
 * only rebuilds for new data, plan items and trip dates must be matched as soon as they change.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class VisitMatchingWithoutRebuildIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    TripService tripService;

    @Inject
    TripPlanItemService planItemService;

    private final Track places = new Track();
    private final double[] home = places.place(49.8420, 24.0310);
    private final double[] office = places.place(49.8350, 24.0750);

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void newPlanItemIsMatchedWithoutWaitingForATimelineRebuild() {
        UUID userId = userWithOfficeDay("visit-new-item");
        Long tripId = tripService.createTrip(userId,
                new CreateTripDto("Work trip", at("05:00"), at("13:00"), null, null)).getId();

        TripPlanItemDto item = planItemService.createTripPlanItem(userId, tripId, planItem("Office", office));

        assertThat(item.getIsVisited()).isTrue();
        // The office stay starts with the arrival at the end of the drive.
        assertThat(item.getVisitedAt()).isBetween(at("08:19"), at("08:22"));
    }

    @Test
    void movedPlanItemIsMatchedAgain() {
        UUID userId = userWithOfficeDay("visit-moved-item");
        Long tripId = tripService.createTrip(userId,
                new CreateTripDto("Work trip", at("05:00"), at("13:00"), null, null)).getId();
        double[] nowhere = places.place(49.9000, 24.2000);
        TripPlanItemDto item = planItemService.createTripPlanItem(userId, tripId, planItem("Office", nowhere));
        assertThat(item.getIsVisited()).isFalse();

        CreateTripPlanItemDto moved = planItem("Office", office);
        TripPlanItemDto updated = planItemService.updateTripPlanItem(userId, tripId, item.getId(),
                new UpdateTripPlanItemDto(moved.getTitle(), null,
                        moved.getLatitude(), moved.getLongitude(), null, null, null, null));

        assertThat(updated.getIsVisited()).isTrue();
    }

    @Test
    void tripDatesChangedToCoverTheVisitMatchIt() {
        UUID userId = userWithOfficeDay("visit-trip-dates");
        Long tripId = tripService.createTrip(userId, new CreateTripDto("Work trip",
                Instant.parse("2026-09-01T05:00:00Z"), Instant.parse("2026-09-01T13:00:00Z"), null, null)).getId();
        TripPlanItemDto item = planItemService.createTripPlanItem(userId, tripId, planItem("Office", office));
        assertThat(item.getIsVisited()).isFalse();

        tripService.updateTrip(userId, tripId, new UpdateTripDto("Work trip", at("05:00"), at("13:00"), null, null,
                null));

        assertThat(planItemService.getTripPlanItems(userId, tripId))
                .singleElement()
                .satisfies(updated -> assertThat(updated.getIsVisited()).isTrue());
    }

    private UUID userWithOfficeDay(String prefix) {
        UUID userId = harness.createUser(prefix);
        harness.ingest(userId, new Track()
                .stay(home, at("06:00"), at("08:00"), 5)
                .drive(home, office, at("08:01"), at("08:20"))
                .stay(office, at("08:21"), at("12:00"), 10)
                .messages());
        assertThat(harness.process(userId)).isEqualTo(Outcome.REBUILT);
        // From here on the real-time job has nothing to rebuild for this user.
        assertThat(harness.process(userId)).isEqualTo(Outcome.GAP_ONLY);
        return userId;
    }

    private static CreateTripPlanItemDto planItem(String title, double[] place) {
        return new CreateTripPlanItemDto(title, null, place[0], place[1], null, null, null, null);
    }

    private static Instant at(String time) {
        return Instant.parse("2026-09-09T" + time + ":00Z");
    }
}
