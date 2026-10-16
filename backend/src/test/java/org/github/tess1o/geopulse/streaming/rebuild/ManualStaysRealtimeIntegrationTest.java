package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.streaming.model.dto.DataGapStayOverrideRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.service.DataGapStayOverrideService;
import org.github.tess1o.geopulse.streaming.service.RealtimeTimelineProcessor.Outcome;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.streaming.rebuild.TimelineReplayHarness.uploadBatches;

/**
 * Stays created by hand (a data gap turned into a stay) are recreated after generation, never produced by it. The
 * real-time job must keep them and must not restart at one, which would make every pass ask for a full rebuild.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class ManualStaysRealtimeIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    DataGapStayOverrideService dataGapStayOverrideService;

    @Inject
    TimelineDataGapRepository dataGapRepository;

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void gapTurnedIntoAStaySurvivesLiveUploadsWithoutFullRebuilds() {
        UUID userId = harness.createUser("manual-stay");
        Track places = new Track();
        double[] home = places.place(49.8420, 24.0310);
        double[] office = places.place(49.8350, 24.0750);
        double[] client = places.place(49.8505, 24.0205);
        double[] meeting = places.place(49.8200, 24.0100);

        // Office in the morning, then nothing until 14:00, when the phone is back at a client's.
        harness.ingest(userId, new Track()
                .stay(home, at("06:00"), at("08:00"), 5)
                .drive(home, office, at("08:01"), at("08:20"))
                .stay(office, at("08:21"), at("10:00"), 10)
                .stay(client, at("14:00"), at("15:00"), 10)
                .messages());
        harness.process(userId);
        // The last office fix before the signal loss is at 09:51.
        TimelineDataGapEntity gap = QuarkusTransaction.requiringNew().call(() ->
                dataGapRepository.findByUserId(userId).stream()
                        .filter(candidate -> candidate.getStartTime().equals(at("09:51")))
                        .findFirst()
                        .orElseThrow());
        // The user marks the gap as a meeting somewhere else: a stay generation never produces, starting where
        // generation never starts one, and not merged with the stays around it.
        DataGapStayOverrideRequest meetingStay = new DataGapStayOverrideRequest();
        meetingStay.setLocationStrategy("SELECTED_LOCATION");
        meetingStay.setLatitude(meeting[0]);
        meetingStay.setLongitude(meeting[1]);
        meetingStay.setLocationName("Meeting");
        QuarkusTransaction.requiringNew().run(() ->
                dataGapStayOverrideService.convertGapToStay(userId, gap.getId(), meetingStay));
        assertThat(harness.snapshot(userId).stays()).anySatisfy(stay -> assertThat(stay).contains("'Meeting'"));
        assertThat(harness.snapshot(userId).gaps()).noneMatch(item -> item.startsWith("GAP " + at("09:51")));

        // The drive home and the evening arrive live. During the drive the meeting is the stay before the latest
        // one, where the real-time job would otherwise restart.
        List<OwnTracksLocationMessage> evening = new Track()
                .drive(client, home, at("15:01"), at("15:20"))
                .stay(home, at("15:21"), at("18:00"), 5)
                .messages();
        for (List<OwnTracksLocationMessage> batch : uploadBatches(evening, 5)) {
            harness.ingest(userId, batch);
            assertThat(harness.process(userId)).isEqualTo(Outcome.REBUILT);
            // No full rebuild asked for: the manual stay is never used as the restart stay.
            assertThat(harness.pendingRebuild(userId)).isEmpty();
            assertThat(harness.snapshot(userId).gaps()).noneMatch(item -> item.startsWith("GAP " + at("09:51")));
            assertThat(harness.snapshot(userId).stays()).anySatisfy(stay -> assertThat(stay).contains("'Meeting'"));
        }
        assertThat(harness.process(userId)).isEqualTo(Outcome.GAP_ONLY);

        // A full rebuild re-applies the manual stay the same way.
        harness.assertMatchesFullRebuild(userId, "manual stay with live uploads");
    }

    private static Instant at(String time) {
        return Instant.parse("2026-09-12T" + time + ":00Z");
    }
}
