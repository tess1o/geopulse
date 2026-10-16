package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geocoding.service.GeocodingProviderFactory;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.streaming.model.TimelineRebuildRequest;
import org.github.tess1o.geopulse.streaming.service.RealtimeTimelineProcessor.Outcome;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When reverse geocoding fails, the stay gets a fallback name and nothing is cached. The real-time job used to retry
 * that for the latest stay simply by rebuilding it on every pass; now the latest stay asks for its own retry.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class GeocodingRetryIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Test
    void latestStayWithoutAPlaceNameIsRetriedUntilGeocodingWorks() {
        GeocodingProviderFactory provider = GeocodingTestMocks.install();
        GeocodingTestMocks.failWith(provider, new IllegalStateException("geocoding provider is down"));
        UUID userId = harness.createUser("geocoding-retry");
        // Coordinates no other test uses, so the shared geocoding cache cannot answer for the failing provider.
        harness.ingest(userId, commute(48.4647, 35.0462));

        assertThat(harness.process(userId)).isEqualTo(Outcome.REBUILT);
        assertThat(harness.snapshot(userId).stays().getLast())
                .contains("'Address not found")
                .contains("geocoding=null");
        TimelineRebuildRequest retry = harness.pendingRebuild(userId).orElseThrow();
        assertThat(retry.rebuildFrom()).isBetween(at("08:19"), at("08:22"));

        // Still failing: the pass retries and asks again.
        assertThat(harness.process(userId)).isEqualTo(Outcome.REBUILT);
        assertThat(harness.pendingRebuild(userId)).isPresent();

        GeocodingTestMocks.install();
        assertThat(harness.process(userId)).isEqualTo(Outcome.REBUILT);
        assertThat(harness.snapshot(userId).stays()).allSatisfy(stay -> {
            assertThat(stay).contains("'Kyiv, Ukraine'");
            assertThat(stay).doesNotContain("geocoding=null");
        });
        assertThat(harness.pendingRebuild(userId)).isEmpty();
        assertThat(harness.process(userId)).isEqualTo(Outcome.GAP_ONLY);
    }

    @Test
    void resolvedPlacesRequestNothing() {
        GeocodingTestMocks.install();
        UUID userId = harness.createUser("geocoding-ok");
        harness.ingest(userId, commute(49.8420, 24.0310));

        assertThat(harness.process(userId)).isEqualTo(Outcome.REBUILT);

        assertThat(harness.pendingRebuild(userId)).isEmpty();
        assertThat(harness.process(userId)).isEqualTo(Outcome.GAP_ONLY);
    }

    private static List<OwnTracksLocationMessage> commute(double homeLat, double homeLon) {
        Track track = new Track();
        double[] home = track.place(homeLat, homeLon);
        double[] office = track.place(homeLat - 0.0070, homeLon + 0.0440);
        return track
                .stay(home, at("06:00"), at("08:00"), 5)
                .drive(home, office, at("08:01"), at("08:20"))
                .stay(office, at("08:21"), at("12:00"), 10)
                .messages();
    }

    private static Instant at(String time) {
        return Instant.parse("2026-09-10T" + time + ":00Z");
    }
}
