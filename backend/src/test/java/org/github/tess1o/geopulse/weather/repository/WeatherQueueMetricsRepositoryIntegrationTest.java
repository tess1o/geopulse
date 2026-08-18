package org.github.tess1o.geopulse.weather.repository;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.weather.model.WeatherTargetStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class WeatherQueueMetricsRepositoryIntegrationTest {

    @Inject
    WeatherSampleTargetRepository targetRepository;

    @Inject
    WeatherBackfillReconciliationRepository reconciliationRepository;

    @Test
    void queueDepthReadsActivateTransactionsOnWorkerThreads() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<String, Long> targets = executor.submit(targetRepository::countByStatus)
                    .get(10, TimeUnit.SECONDS);
            long dirtyRanges = executor.submit(reconciliationRepository::countPendingUserRanges)
                    .get(10, TimeUnit.SECONDS);
            boolean hasStaleClaims = executor.submit(() ->
                            targetRepository.hasStaleInProgressTargets(Instant.now().minusSeconds(3600)))
                    .get(10, TimeUnit.SECONDS);

            assertThat(targets).containsKeys(
                    WeatherTargetStatus.PENDING.name(),
                    WeatherTargetStatus.IN_PROGRESS.name(),
                    WeatherTargetStatus.COMPLETED.name());
            assertThat(dirtyRanges).isNotNegative();
            assertThat(hasStaleClaims).isFalse();
        }
    }
}
