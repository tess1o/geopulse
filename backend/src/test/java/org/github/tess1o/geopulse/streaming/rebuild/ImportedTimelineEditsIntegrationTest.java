package org.github.tess1o.geopulse.streaming.rebuild;

import static org.github.tess1o.geopulse.testsupport.CorrectionTestPayloads.put;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.edits.ActivityCorrectionService;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditService;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A GeoPulse export carries the user's manual corrections. Importing it rebuilds the timeline from the imported points;
 * the corrections must be part of that rebuild, so the imported timeline shows them without anything else to do.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class ImportedTimelineEditsIntegrationTest {

    @Inject TimelineReplayHarness harness;
    @Inject ManualEditsSupport edits;
    @Inject TimelineEditService timelineEdits;
    @Inject ActivityCorrectionService corrections;
    @Inject TimelineDataGapRepository dataGapRepository;

    private final CommuteWithStops day = new CommuteWithStops();

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void importedTimelineShowsTheExportedCorrections() {
        UUID original = day.ingest(harness, "edits-export");
        // Back at the office in the afternoon after the phone was off since noon.
        harness.ingest(original, day.track.stay(day.office, at("16:00"), at("17:00"), 10).messages());
        harness.fullRebuild(original);

        setMovementType(original, edits.tripCovering(original, at("08:16"), at("08:20")), "TRAIN");
        harness.fullRebuild(original);
        edits.split(original, at("08:16"), at("08:20"), day.bakery, "Bakery");

        TimelineDataGapEntity gap = QuarkusTransaction.requiringNew().call(() ->
                dataGapRepository.findByUserId(original).stream()
                        .filter(candidate -> candidate.getEndTime().equals(at("16:00")))
                        .findFirst()
                        .orElseThrow());
        var gym = new ActivityCorrectionService.Activity();
        gym.setSegmentType("STAY");
        gym.setStartTime(gap.getStartTime());
        gym.setEndTime(gap.getEndTime());
        gym.setLatitude(day.pharmacy[0] + 0.004);
        gym.setLongitude(day.pharmacy[1]);
        gym.setLocationName("Gym");
        corrections.save(original, new ActivityCorrectionService.Request(null, List.of(gym), null, null));
        harness.fullRebuild(original);

        List<String> exported = harness.snapshot(original).contentAcrossUsers();
        assertThat(exported)
                .anySatisfy(item -> assertThat(item).contains("'Bakery'"))
                .anySatisfy(item -> assertThat(item).contains("'Gym'"))
                .anySatisfy(item -> assertThat(item).contains(" TRAIN "));

        byte[] export = edits.exportOf(original, at("00:00"), at("23:59"));
        var exportedEdits = harness.readExportJson(export, "timeline-edits.json").path("edits");
        List<String> kinds = new ArrayList<>();
        exportedEdits.forEach(edit -> kinds.add(edit.path("kind").asText()));
        assertThat(kinds).contains("SET_MOVEMENT_TYPE", "FORCE_STAY");

        UUID imported = harness.createUser("edits-import");
        edits.importInto(imported, export);

        assertThat(harness.snapshot(imported).contentAcrossUsers())
                .as("imported timeline")
                .containsExactlyElementsOf(exported);
    }

    private void setMovementType(UUID userId, TimelineTripEntity trip, String movementType) {
        var request = new TimelineEditRequest();
        request.setKind(TimelineEditKind.SET_MOVEMENT_TYPE);
        request.setAnchorStart(trip.getTimestamp());
        request.setAnchorEnd(trip.getTimestamp().plusSeconds(trip.getTripDuration()));
        request.setAffectedFrom(trip.getTimestamp());
        request.setAffectedTo(trip.getTimestamp().plusSeconds(trip.getTripDuration()));
        put(request, "sourceType", "TRIP");
        put(request, "sourceStartLatitude", trip.getStartPoint().getY());
        put(request, "sourceStartLongitude", trip.getStartPoint().getX());
        put(request, "sourceEndLatitude", trip.getEndPoint().getY());
        put(request, "sourceEndLongitude", trip.getEndPoint().getX());
        put(request, "movementType", movementType);
        timelineEdits.create(userId, request);
    }

    private static Instant at(String time) {
        return CommuteWithStops.at(time);
    }
}
