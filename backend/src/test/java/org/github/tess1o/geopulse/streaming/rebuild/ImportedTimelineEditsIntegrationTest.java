package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.model.dto.DataGapStayOverrideRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.service.DataGapStayOverrideService;
import org.github.tess1o.geopulse.streaming.service.TripMovementTypeOverrideService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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

    @Inject
    TimelineReplayHarness harness;

    @Inject
    ManualEditsSupport edits;

    @Inject
    TripMovementTypeOverrideService movementTypeOverrideService;

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
    void importedTimelineShowsTheExportedCorrections() {
        UUID original = day.ingest(harness, "edits-export");
        // Back at the office in the afternoon after the phone was off since noon.
        harness.ingest(original, day.track.stay(day.office, at("16:00"), at("17:00"), 10).messages());
        harness.fullRebuild(original);

        movementTypeOverrideService.setManualMovementType(original,
                edits.tripCovering(original, at("08:16"), at("08:20")).getId(), TripType.TRAIN);
        edits.split(original, at("08:16"), at("08:20"), day.bakery, "Bakery");
        TimelineDataGapEntity gap = QuarkusTransaction.requiringNew().call(() ->
                dataGapRepository.findByUserId(original).stream()
                        .filter(candidate -> candidate.getEndTime().equals(at("16:00")))
                        .findFirst()
                        .orElseThrow());
        DataGapStayOverrideRequest atTheGym = new DataGapStayOverrideRequest();
        atTheGym.setLocationStrategy("SELECTED_LOCATION");
        atTheGym.setLatitude(day.pharmacy[0] + 0.004);
        atTheGym.setLongitude(day.pharmacy[1]);
        atTheGym.setLocationName("Gym");
        QuarkusTransaction.requiringNew().run(() ->
                dataGapStayOverrideService.convertGapToStay(original, gap.getId(), atTheGym));
        List<String> exported = harness.snapshot(original).contentAcrossUsers();
        assertThat(exported)
                .anySatisfy(item -> assertThat(item).contains("'Bakery'"))
                .anySatisfy(item -> assertThat(item).contains("'Gym'"))
                .anySatisfy(item -> assertThat(item).contains(" TRAIN "));

        byte[] export = edits.exportOf(original, at("00:00"), at("23:59"));
        var exportedSplit = harness.readExportJson(export, "timeline-overrides.json")
                .path("tripStaySplitOverrides").get(0);
        assertThat(exportedSplit.path("sourceTripMovementType").asText())
                .as("the manual type the split inherited").isEqualTo("TRAIN");
        assertThat(exportedSplit.path("stayLocationNameCustom").asBoolean())
                .as("the split stay's name was typed for it").isTrue();

        UUID imported = harness.createUser("edits-import");
        edits.importInto(imported, export);

        assertThat(harness.snapshot(imported).contentAcrossUsers())
                .as("imported timeline")
                .containsExactlyElementsOf(exported);
    }

    private static Instant at(String time) {
        return CommuteWithStops.at(time);
    }
}
