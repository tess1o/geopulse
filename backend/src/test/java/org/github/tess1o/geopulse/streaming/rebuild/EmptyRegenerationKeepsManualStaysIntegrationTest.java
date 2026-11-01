package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.streaming.model.dto.DataGapStayOverrideRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.service.DataGapStayOverrideService;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A regeneration with nothing to process still deletes the timeline from its restart point. What the user made by
 * hand must survive that: a correction is not derived from the points that are gone.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class EmptyRegenerationKeepsManualStaysIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    DataGapStayOverrideService dataGapStayOverrideService;

    @Inject
    TimelineDataGapRepository dataGapRepository;

    @Inject
    GpsPointService gpsPointService;

    @Inject
    StreamingTimelineGenerationService timelineGenerationService;

    private final Track track = new Track();
    private final double[] home = track.place(49.8420, 24.0310);
    private final double[] office = track.place(49.8350, 24.0750);
    private final double[] client = track.place(49.8505, 24.0205);
    private final double[] meeting = track.place(49.8200, 24.0100);

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void aRegenerationWithNoPointsLeavesAManualStayInPlace() {
        UUID userId = userWithAManualStay("empty-regeneration");

        deleteAllPoints(userId);
        timelineGenerationService.generateTimelineFromTimestamp(userId, at("09:00"));

        assertThat(harness.snapshot(userId).stays())
                .as("the manual stay after a regeneration with no GPS points left")
                .anySatisfy(stay -> assertThat(stay).contains("'Meeting'"));
    }

    @Test
    void pointsThatProduceNoEventsAlsoLeaveTheManualStayInPlace() {
        UUID userId = userWithAManualStay("empty-events");

        deleteAllPoints(userId);
        // One point with an unusable accuracy is counted but filtered out: generation produces no events.
        harness.ingest(userId, List.of(Track.message(meeting[0], meeting[1], at("09:30"), 500.0, 0.0)));
        timelineGenerationService.generateTimelineFromTimestamp(userId, at("08:30"));

        assertThat(harness.snapshot(userId).stays())
                .as("the manual stay after a regeneration that produced no events")
                .anySatisfy(stay -> assertThat(stay).contains("'Meeting'"));
    }

    private UUID userWithAManualStay(String prefix) {
        UUID userId = harness.createUser(prefix);
        harness.ingest(userId, track
                .stay(home, at("06:00"), at("08:00"), 5)
                .drive(home, office, at("08:01"), at("08:20"))
                .stay(office, at("08:21"), at("10:00"), 10)
                .stay(client, at("14:00"), at("15:00"), 10)
                .messages());
        harness.process(userId);
        TimelineDataGapEntity gap = QuarkusTransaction.requiringNew().call(() ->
                dataGapRepository.findByUserId(userId).stream()
                        .filter(candidate -> candidate.getStartTime().equals(at("09:51")))
                        .findFirst()
                        .orElseThrow());
        DataGapStayOverrideRequest meetingStay = new DataGapStayOverrideRequest();
        meetingStay.setLocationStrategy("SELECTED_LOCATION");
        meetingStay.setLatitude(meeting[0]);
        meetingStay.setLongitude(meeting[1]);
        meetingStay.setLocationName("Meeting");
        QuarkusTransaction.requiringNew().run(() ->
                dataGapStayOverrideService.convertGapToStay(userId, gap.getId(), meetingStay));
        assertThat(harness.snapshot(userId).stays()).anySatisfy(stay -> assertThat(stay).contains("'Meeting'"));
        return userId;
    }

    private void deleteAllPoints(UUID userId) {
        List<Long> allPoints = harness.pointIds(userId, Instant.EPOCH, Instant.parse("2030-01-01T00:00:00Z"));
        QuarkusTransaction.requiringNew().run(() -> gpsPointService.deleteGpsPoints(allPoints, userId));
    }

    private static Instant at(String time) {
        return Instant.parse("2026-09-12T" + time + ":00Z");
    }
}
