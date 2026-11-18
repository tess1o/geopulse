package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.edits.ActivityCorrectionService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A split made on a leg of an earlier split is a FORCE_STAY correction anchored to that leg, which only exists once
 * the earlier split has been applied. Applying the corrections in sequence order must keep both.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class NestedTripSplitIntegrationTest {

    @Inject TimelineReplayHarness harness;
    @Inject ActivityCorrectionService corrections;

    private final CommuteWithStops day = new CommuteWithStops();

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void splitOfTheFirstLegOfASplitTripSurvivesAFullRebuild() {
        UUID userId = day.ingest(harness, "nested-split");
        // The pharmacy first, then the bakery on the leg from home to the pharmacy.
        split(userId, "08:27", "08:31", day.pharmacy, "Pharmacy");
        split(userId, "08:16", "08:20", day.bakery, "Bakery");
        List<String> afterSplits = harness.snapshot(userId).content();
        assertThat(afterSplits)
                .anySatisfy(item -> assertThat(item).contains("'Bakery'"))
                .anySatisfy(item -> assertThat(item).contains("'Pharmacy'"));

        harness.fullRebuild(userId);

        assertThat(harness.snapshot(userId).content())
                .as("timeline after a full rebuild")
                .anySatisfy(item -> assertThat(item).contains("'Bakery'"))
                .anySatisfy(item -> assertThat(item).contains("'Pharmacy'"));
        // A second rebuild changes nothing: the corrections do not come and go.
        harness.assertMatchesFullRebuild(userId, "nested splits");
    }

    private void split(UUID userId, String from, String to, double[] place, String name) {
        var stay = new ActivityCorrectionService.Activity();
        stay.setSegmentType("STAY");
        stay.setStartTime(at(from));
        stay.setEndTime(at(to));
        stay.setLatitude(place[0]);
        stay.setLongitude(place[1]);
        stay.setLocationName(name);
        var saved = corrections.save(userId, new ActivityCorrectionService.Request(null, List.of(stay), null, null));
        harness.regenerateFrom(userId, saved.affectedFrom());
    }

    private static Instant at(String time) {
        return CommuteWithStops.at(time);
    }
}
