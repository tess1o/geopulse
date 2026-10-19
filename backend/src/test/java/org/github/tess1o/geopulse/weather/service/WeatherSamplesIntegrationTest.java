package org.github.tess1o.geopulse.weather.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.TimelineSeeder;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.weather.dto.WeatherSampleDTO;
import org.github.tess1o.geopulse.weather.dto.WeatherSamplesResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Weather sample lookup over seeded samples, using the server's default weather settings (enabled, Open-Meteo).
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
class WeatherSamplesIntegrationTest {

    @Inject
    WeatherService weatherService;

    @Inject
    TimelineSeeder seed;

    private UUID ownerId;

    /*
     * Owner's samples: Kyiv 5.0° at Mar 1 10:00, Kyiv 7.5° at Mar 1 11:00, Lviv 3.0° at Mar 1 12:00,
     * Kyiv 1.0° at Feb 1 10:00.
     */
    @BeforeEach
    @Transactional
    void setUp() {
        UserEntity owner = seed.user("weather-owner", "UTC");
        ownerId = owner.getId();
        seed.weatherSample(owner, Instant.parse("2026-03-01T11:00:00Z"), 50.45, 30.52, 7.5);
        seed.weatherSample(owner, Instant.parse("2026-03-01T10:00:00Z"), 50.45, 30.52, 5.0);
        seed.weatherSample(owner, Instant.parse("2026-03-01T12:00:00Z"), 49.84, 24.03, 3.0);
        seed.weatherSample(owner, Instant.parse("2026-02-01T10:00:00Z"), 50.45, 30.52, 1.0);

        UserEntity other = seed.user("weather-other", "UTC");
        seed.weatherSample(other, Instant.parse("2026-03-01T10:30:00Z"), 50.45, 30.52, 20.0);
    }

    @Test
    void findSamples_returnsTheUsersSamplesInTheRangeOldestFirst() {
        WeatherSamplesResponse response = weatherService.findSamples(ownerId,
                Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-03-01T23:59:59Z"), null, null, null, null);

        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getSamples()).extracting(WeatherSampleDTO::getObservedAt).containsExactly(
                Instant.parse("2026-03-01T10:00:00Z"),
                Instant.parse("2026-03-01T11:00:00Z"),
                Instant.parse("2026-03-01T12:00:00Z"));
        assertThat(response.getSamples()).extracting(WeatherSampleDTO::getTemperature).containsExactly(5.0, 7.5, 3.0);
    }

    @Test
    void findSamples_rangeIsInclusive() {
        WeatherSamplesResponse response = weatherService.findSamples(ownerId,
                Instant.parse("2026-03-01T11:00:00Z"), Instant.parse("2026-03-01T12:00:00Z"), null, null, null, null);

        assertThat(response.getSamples()).extracting(WeatherSampleDTO::getTemperature).containsExactly(7.5, 3.0);
    }

    @Test
    void findSamples_boundingBoxKeepsOnlySamplesInside() {
        WeatherSamplesResponse kyiv = weatherService.findSamples(ownerId,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T23:59:59Z"),
                50.0, 30.0, 51.0, 31.0);

        assertThat(kyiv.getSamples()).extracting(WeatherSampleDTO::getTemperature).containsExactly(1.0, 5.0, 7.5);
        assertThat(kyiv.getSamples()).extracting(WeatherSampleDTO::getLatitude).containsOnly(50.45);
    }
}
