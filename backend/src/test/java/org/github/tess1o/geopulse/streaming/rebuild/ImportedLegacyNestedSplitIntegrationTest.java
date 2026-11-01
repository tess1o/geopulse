package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripStaySplitOverrideEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripStaySplitOverrideRepository;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Old exports list split overrides by stay start time. A split made on a leg of an already split trip can therefore
 * appear before its parent after import, where ids are assigned in file order. The child must still find its leg and
 * the parent must still find the original trip.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class ImportedLegacyNestedSplitIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    ManualEditsSupport edits;

    @Inject
    TimelineTripStaySplitOverrideRepository overrideRepository;

    private final CommuteWithStops day = new CommuteWithStops();
    private final double[] onTheWay = day.track.place(49.8392, 24.0483);

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void aChildSplitListedBeforeItsParentStillSurvivesImportAndParentUndo() {
        UUID original = day.ingest(harness, "legacy-nested-export");
        edits.split(original, at("08:16"), at("08:20"), day.bakery, "Bakery");
        edits.split(original, at("08:12"), at("08:14"), onTheWay, "Coffee");
        assertThat(harness.snapshot(original).stays())
                .anySatisfy(stay -> assertThat(stay).contains("'Bakery'"))
                .anySatisfy(stay -> assertThat(stay).contains("'Coffee'"));

        byte[] legacyExport = harness.exportWithLegacySplitOrder(
                edits.exportOf(original, at("00:00"), at("23:59")));
        UUID imported = harness.createUser("legacy-nested-import");
        edits.importInto(imported, legacyExport);
        edits.awaitTimelineJobs(imported);

        assertThat(harness.snapshot(imported).stays())
                .as("both nested split stays after importing an old-ordered export")
                .anySatisfy(stay -> assertThat(stay).contains("'Bakery'"))
                .anySatisfy(stay -> assertThat(stay).contains("'Coffee'"));

        TimelineTripStaySplitOverrideEntity importedParent = QuarkusTransaction.requiringNew().call(() ->
                overrideRepository.findByUserId(imported).stream()
                        .filter(override -> override.getStayStartTime().equals(at("08:16")))
                        .findFirst()
                        .orElseThrow());
        edits.undoSplit(imported, importedParent.getId());

        assertThat(QuarkusTransaction.requiringNew().call(() -> overrideRepository.findByUserId(imported)))
                .as("undoing the parent also means its child split can no longer apply")
                .isEmpty();
        assertThat(harness.snapshot(imported).stays())
                .as("stays after undoing the parent split")
                .noneSatisfy(stay -> assertThat(stay).contains("'Bakery'"))
                .noneSatisfy(stay -> assertThat(stay).contains("'Coffee'"));
    }

    private static Instant at(String time) {
        return CommuteWithStops.at(time);
    }
}
