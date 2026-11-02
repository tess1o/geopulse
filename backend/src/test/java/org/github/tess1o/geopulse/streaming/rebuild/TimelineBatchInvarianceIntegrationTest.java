package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.engine.StreamingTimelineProcessor;
import org.github.tess1o.geopulse.streaming.iterator.StreamingGpsIterable;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Page boundaries must not alter stop detection, trip evidence, places or explanations. */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineBatchInvarianceIntegrationTest {

    @Inject TimelineReplayHarness harness;
    @Inject GpsPointRepository gpsPoints;
    @Inject TimelineConfigurationProvider configuration;
    @Inject StreamingTimelineProcessor processor;

    @Test
    void identicalEventsAndStatisticsAcrossGpsPageSizes() {
        GeocodingTestMocks.install();
        UUID user = harness.createUserWithFavoriteAreas("page-invariance", SyntheticDays.NAMED_PLACES);
        harness.ingest(user, SyntheticDays.generate(42, LocalDate.of(2026, 9, 3), 2));

        List<TimelineEvent> expected = process(user, 10_000);
        assertThat(expected).hasSizeGreaterThan(5);
        for (int pageSize : new int[]{1, 7, 64}) {
            assertThat(process(user, pageSize))
                    .as("all event fields, including explanations and statistics, at page size %s", pageSize)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    private List<TimelineEvent> process(UUID user, int pageSize) {
        return QuarkusTransaction.requiringNew().call(() -> processor.processPoints(
                new StreamingGpsIterable(gpsPoints, user, Instant.EPOCH, pageSize),
                configuration.getConfigurationForUser(user), user));
    }
}
