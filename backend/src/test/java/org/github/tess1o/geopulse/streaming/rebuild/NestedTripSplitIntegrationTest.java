package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A split made on a leg of an earlier split is anchored to that leg, which only exists once the earlier split has been
 * applied. Re-applying splits must apply the earlier one first, or the later one finds no matching trip.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class NestedTripSplitIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    ManualEditsSupport edits;

    private final CommuteWithStops day = new CommuteWithStops();

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void splitOfTheFirstLegOfASplitTripSurvivesAFullRebuild() {
        UUID userId = day.ingest(harness, "nested-split");
        // The pharmacy first, then the bakery on the leg from home to the pharmacy.
        edits.split(userId, at("08:27"), at("08:31"), day.pharmacy, "Pharmacy");
        edits.split(userId, at("08:16"), at("08:20"), day.bakery, "Bakery");
        List<String> afterSplits = harness.snapshot(userId).content();
        assertThat(afterSplits)
                .anySatisfy(item -> assertThat(item).contains("'Bakery'"))
                .anySatisfy(item -> assertThat(item).contains("'Pharmacy'"));

        harness.fullRebuild(userId);

        assertThat(harness.snapshot(userId).content())
                .as("timeline after a full rebuild")
                .anySatisfy(item -> assertThat(item).contains("'Bakery'"))
                .anySatisfy(item -> assertThat(item).contains("'Pharmacy'"));
        // A second rebuild changes nothing: the splits do not come and go.
        harness.assertMatchesFullRebuild(userId, "nested splits");
    }

    private static Instant at(String time) {
        return CommuteWithStops.at(time);
    }
}
