package org.github.tess1o.geopulse.streaming.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.importdata.model.DebugImportRequest;
import org.github.tess1o.geopulse.importdata.service.DebugImportService;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestUserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Replays the debug export attached to GitHub issue #627: office stay -> drive home -> data gap -> home stay.
 * The trip post-processor used to synthesize a "continuity" trip from the office stay to the home stay across
 * the data gap, producing a WALK trip that overlapped both the real CAR trip and the gap.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class DataGapContinuityTripIntegrationTest {

    private static final String DEBUG_EXPORT = "/timeline-fixtures/issue-627-debug-export.zip";

    @Inject
    DebugImportService debugImportService;

    @Inject
    TestUserFactory testUserFactory;

    @Inject
    TimelineTripRepository timelineTripRepository;

    @Inject
    TimelineDataGapRepository timelineDataGapRepository;

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void shouldNotCreateContinuityTripAcrossDataGapAfterRealTrip() throws IOException {
        UUID userId = testUserFactory.createUser("issue-627").userId();

        debugImportService.importDebugData(userId, readDebugExport(), new DebugImportRequest(true, true));

        List<TimelineTripEntity> trips = timelineTripRepository.findByUser(userId).stream()
                .sorted(Comparator.comparing(TimelineTripEntity::getTimestamp))
                .toList();
        List<TimelineDataGapEntity> gaps = timelineDataGapRepository.findByUserId(userId);

        // The drive home before the gap is the only trip of the afternoon.
        List<TimelineTripEntity> afternoonTrips = trips.stream()
                .filter(trip -> trip.getTimestamp().isAfter(Instant.parse("2026-10-08T11:00:00Z")))
                .toList();
        assertThat(afternoonTrips).hasSize(1);
        TimelineTripEntity driveHome = afternoonTrips.getFirst();
        assertThat(driveHome.getMovementType()).isEqualTo("CAR");
        assertThat(driveHome.getTimestamp()).isEqualTo(Instant.parse("2026-10-08T11:48:07Z"));
        assertThat(endOf(driveHome)).isEqualTo(Instant.parse("2026-10-08T11:59:00Z"));

        assertThat(gaps).anySatisfy(gap -> {
            assertThat(gap.getStartTime()).isEqualTo(Instant.parse("2026-10-08T11:59:00Z"));
            assertThat(gap.getEndTime()).isEqualTo(Instant.parse("2026-10-08T18:51:15Z"));
        });

        for (int i = 1; i < trips.size(); i++) {
            assertThat(trips.get(i).getTimestamp())
                    .as("trip %s must start after previous trip ends", trips.get(i).getId())
                    .isAfterOrEqualTo(endOf(trips.get(i - 1)));
        }
        for (TimelineTripEntity trip : trips) {
            for (TimelineDataGapEntity gap : gaps) {
                boolean overlaps = trip.getTimestamp().isBefore(gap.getEndTime())
                        && endOf(trip).isAfter(gap.getStartTime());
                assertThat(overlaps)
                        .as("trip %s (%s, %ss) overlaps data gap %s..%s", trip.getId(), trip.getTimestamp(),
                                trip.getTripDuration(), gap.getStartTime(), gap.getEndTime())
                        .isFalse();
            }
        }
    }

    private static Instant endOf(TimelineTripEntity trip) {
        return trip.getTimestamp().plusSeconds(trip.getTripDuration());
    }

    private static byte[] readDebugExport() throws IOException {
        try (InputStream input = DataGapContinuityTripIntegrationTest.class.getResourceAsStream(DEBUG_EXPORT)) {
            assertThat(input).as("missing fixture %s", DEBUG_EXPORT).isNotNull();
            return input.readAllBytes();
        }
    }
}
