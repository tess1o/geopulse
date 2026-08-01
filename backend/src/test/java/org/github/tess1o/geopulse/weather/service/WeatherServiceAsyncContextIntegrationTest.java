package org.github.tess1o.geopulse.weather.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class WeatherServiceAsyncContextIntegrationTest {

    @Inject
    WeatherService weatherService;

    @Inject
    EntityManager entityManager;

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() ->
                entityManager.createNativeQuery("DELETE FROM weather_sample_targets").executeUpdate());
    }

    @Test
    void fetchQueuedSamplesCanRunFromAsyncWeatherThreadWithoutRequestContextException() {
        CompletableFuture<Integer> fetch = CompletableFuture.supplyAsync(weatherService::fetchQueuedSamples);

        assertThatCode(() -> assertThat(fetch.get(10, TimeUnit.SECONDS)).isZero())
                .doesNotThrowAnyException();
    }
}
