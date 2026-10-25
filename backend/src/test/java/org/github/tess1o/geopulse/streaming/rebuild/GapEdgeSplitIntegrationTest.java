package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.model.dto.TripStaySplitRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.service.TripStaySplitOverrideService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A split stay must end inside its trip. When a data gap starts exactly at the trip's end, that rule is what stops a
 * stay ending on the gap edge, before the gap overlap check is reached. A split one second earlier is accepted, so the
 * gap boundary is not rejecting valid splits.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class GapEdgeSplitIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    ManualEditsSupport edits;

    @Inject
    TripStaySplitOverrideService splitService;

    @Inject
    TimelineDataGapRepository dataGapRepository;

    private final Track track = new Track();
    private final double[] home = track.place(49.8420, 24.0310);
    private final double[] office = track.place(49.8350, 24.0750);
    private final double[] client = track.place(49.8505, 24.0205);

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void onlyTheTripBoundaryRejectsAStayEndingOnTheGapEdge() {
        UUID userId = harness.createUser("gap-edge-split");
        harness.ingest(userId, track
                .stay(home, at("06:00"), at("08:00"), 10)
                .drive(home, office, at("08:01"), at("08:20"))
                .stay(client, at("14:00"), at("15:00"), 10)
                .messages());
        harness.fullRebuild(userId);

        TimelineTripEntity trip = edits.tripCovering(userId, at("08:05"), at("08:15"));
        Instant tripEnd = trip.getTimestamp().plusSeconds(trip.getTripDuration());
        Instant stayStart = trip.getTimestamp().plusSeconds(60);
        assertThat(QuarkusTransaction.requiringNew().call(() -> dataGapRepository.findByUserId(userId)))
                .as("the gap starts where the trip ends")
                .anySatisfy(gap -> assertThat(gap.getStartTime()).isEqualTo(tripEnd));

        Instant anchor = at("08:10");
        double progress = (double) java.time.Duration.between(trip.getTimestamp(), anchor).toSeconds()
                / trip.getTripDuration();
        double latitude = trip.getStartPoint().getY()
                + (trip.getEndPoint().getY() - trip.getStartPoint().getY()) * progress;
        double longitude = trip.getStartPoint().getX()
                + (trip.getEndPoint().getX() - trip.getStartPoint().getX()) * progress;

        TripStaySplitRequest endingOnTheEdge = request(trip, stayStart, tripEnd, anchor, latitude, longitude);
        assertThatThrownBy(() -> splitService.previewSplit(userId, trip.getId(), endingOnTheEdge))
                .as("a stay ending at the trip's end")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fully inside the trip");

        Instant beforeTheGap = tripEnd.minusSeconds(120);
        assertThat(splitService.previewSplit(userId, trip.getId(),
                request(trip, stayStart, beforeTheGap, anchor, latitude, longitude)))
                .as("a stay ending before the gap")
                .isPresent();
    }

    private static TripStaySplitRequest request(TimelineTripEntity trip, Instant from, Instant to, Instant anchor,
                                                double latitude, double longitude) {
        TripStaySplitRequest request = new TripStaySplitRequest();
        request.setStayStartTime(from);
        request.setStayEndTime(to);
        request.setAnchorTimestamp(anchor);
        request.setLatitude(latitude);
        request.setLongitude(longitude);
        request.setLocationName("Stop");
        return request;
    }

    private static Instant at(String time) {
        return Instant.parse("2026-09-17T" + time + ":00Z");
    }
}
