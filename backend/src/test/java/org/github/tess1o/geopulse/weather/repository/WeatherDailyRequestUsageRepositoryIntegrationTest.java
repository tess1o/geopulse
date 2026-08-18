package org.github.tess1o.geopulse.weather.repository;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class WeatherDailyRequestUsageRepositoryIntegrationTest {

    @Inject
    WeatherDailyRequestUsageRepository repository;

    @Inject
    EntityManager entityManager;

    @BeforeEach
    void clearUsage() {
        QuarkusTransaction.requiringNew().run(() ->
                entityManager.createNativeQuery("DELETE FROM weather_daily_request_usage").executeUpdate());
    }

    @Test
    void reservesBackfillOnlyBelowOngoingReserveAndLetsOngoingUseTheRemainder() {
        assertThat(repository.tryReserve(WeatherTargetSource.HISTORICAL_BACKFILL, 5, 2)).isTrue();
        assertThat(repository.tryReserve(WeatherTargetSource.HISTORICAL_BACKFILL, 5, 2)).isTrue();
        assertThat(repository.tryReserve(WeatherTargetSource.HISTORICAL_BACKFILL, 5, 2)).isTrue();
        assertThat(repository.tryReserve(WeatherTargetSource.HISTORICAL_BACKFILL, 5, 2)).isFalse();

        assertThat(repository.tryReserve(WeatherTargetSource.ONGOING, 5, 2)).isTrue();
        assertThat(repository.tryReserve(WeatherTargetSource.ONGOING, 5, 2)).isTrue();
        assertThat(repository.tryReserve(WeatherTargetSource.ONGOING, 5, 2)).isFalse();

        WeatherDailyRequestUsage usage = repository.today();
        assertThat(usage.requestCount()).isEqualTo(5);
        assertThat(usage.backfillRequestCount()).isEqualTo(3);
        assertThat(usage.ongoingRequestCount()).isEqualTo(2);
    }

    @Test
    void reservesSeveralConnectionTestCallsAtomically() {
        assertThat(repository.tryReserve(WeatherTargetSource.ONGOING, 1, 3, 0)).isTrue();
        assertThat(repository.tryReserve(WeatherTargetSource.ONGOING, 3, 3, 0)).isFalse();
        assertThat(repository.today().requestCount()).isEqualTo(1);

        assertThat(repository.tryReserve(WeatherTargetSource.ONGOING, 2, 3, 0)).isTrue();
        assertThat(repository.today().requestCount()).isEqualTo(3);
        assertThat(repository.today().ongoingRequestCount()).isEqualTo(3);
    }
}
