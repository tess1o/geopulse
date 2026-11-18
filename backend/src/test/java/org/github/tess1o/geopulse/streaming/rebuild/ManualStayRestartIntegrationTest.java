package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.EditGpsPointDto;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.streaming.edits.ActivityCorrectionService;
import org.github.tess1o.geopulse.streaming.service.AsyncTimelineGenerationService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A stay made by splitting a trip is a FORCE_STAY correction recreated on every pass. A regeneration that restarts at
 * that stay must still apply the correction, and undoing a later split must leave the earlier one in place.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class ManualStayRestartIntegrationTest {

    @Inject TimelineReplayHarness harness;
    @Inject ActivityCorrectionService corrections;
    @Inject ManualEditsSupport edits;
    @Inject GpsPointService gpsPointService;
    @Inject AsyncTimelineGenerationService asyncTimelineGenerationService;

    private final CommuteWithStops day = new CommuteWithStops();

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void splitStaySurvivesAGpsPointEditAfterIt() {
        UUID userId = day.ingest(harness, "split-gps-edit");
        split(userId, "08:16", "08:20", day.bakery, "Bakery");
        assertThat(harness.snapshot(userId).stays()).anySatisfy(stay -> assertThat(stay).contains("'Bakery'"));

        // The user corrects a point of the second leg. The GPS data page schedules a regeneration from its time.
        Long pointId = harness.pointIds(userId, at("08:36"), at("08:37")).getFirst();
        Instant affected = QuarkusTransaction.requiringNew().call(() -> gpsPointService.updateGpsPoint(pointId,
                new EditGpsPointDto(new EditGpsPointDto.CoordinatesDto(49.8360, 24.0680), 45.0, 8.0), userId)
                .earliestAffectedTimestamp());
        UUID jobId = asyncTimelineGenerationService.scheduleTimelineRegenerationFromTimestamp(userId, affected).jobId();
        edits.awaitJob(jobId);

        assertThat(harness.snapshot(userId).stays())
                .as("the split stay after a GPS edit later in the trip")
                .anySatisfy(stay -> assertThat(stay).contains("'Bakery'"));
    }

    @Test
    void undoingALaterSplitKeepsAnEarlierOne() {
        UUID userId = day.ingest(harness, "split-undo-later");
        split(userId, "08:16", "08:20", day.bakery, "Bakery");
        // The second leg, from the bakery on, is split again at the pharmacy.
        var pharmacy = split(userId, "08:27", "08:31", day.pharmacy, "Pharmacy");
        assertThat(harness.snapshot(userId).stays())
                .anySatisfy(stay -> assertThat(stay).contains("'Bakery'"))
                .anySatisfy(stay -> assertThat(stay).contains("'Pharmacy'"));
        var bothSplits = harness.snapshot(userId).contentAcrossUsers();

        corrections.changeStatus(userId, pharmacy.groupId(), false);
        harness.fullRebuild(userId);

        assertThat(harness.snapshot(userId).stays())
                .as("stays after undoing the pharmacy split")
                .anySatisfy(stay -> assertThat(stay).contains("'Bakery'"))
                .noneSatisfy(stay -> assertThat(stay).contains("'Pharmacy'"));

        // Their rebuild ranges overlap, but the two fills own disjoint intervals.
        corrections.changeStatus(userId, pharmacy.groupId(), true);
        harness.fullRebuild(userId);
        assertThat(harness.snapshot(userId).contentAcrossUsers()).containsExactlyElementsOf(bothSplits);
    }

    private ActivityCorrectionService.Saved split(UUID userId, String from, String to, double[] place, String name) {
        var stay = new ActivityCorrectionService.Activity();
        stay.setSegmentType("STAY");
        stay.setStartTime(at(from));
        stay.setEndTime(at(to));
        stay.setLatitude(place[0]);
        stay.setLongitude(place[1]);
        stay.setLocationName(name);
        var saved = corrections.save(userId, new ActivityCorrectionService.Request(null, List.of(stay), null, null));
        harness.regenerateFrom(userId, saved.affectedFrom());
        return saved;
    }

    private static Instant at(String time) {
        return CommuteWithStops.at(time);
    }
}
