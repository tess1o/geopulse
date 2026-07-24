package org.github.tess1o.geopulse.weather.repository;

import org.github.tess1o.geopulse.weather.dto.WeatherSampleDTO;
import org.github.tess1o.geopulse.weather.model.WeatherSampleEntity;
import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class WeatherSampleRepositoryTest {

    @Test
    void toDtoUsesRequestedCoordinatesInsteadOfDeduplicationBuckets() {
        WeatherSampleEntity sample = WeatherSampleEntity.builder()
                .provider("OPEN_METEO")
                .source(WeatherTargetSource.HISTORICAL_BACKFILL)
                .requestedLatitude(49.54821)
                .requestedLongitude(25.59631)
                .latitudeBucket(49.55)
                .longitudeBucket(25.60)
                .observedAt(Instant.parse("2026-07-23T18:00:00Z"))
                .fetchedAt(Instant.parse("2026-07-23T21:10:45Z"))
                .build();

        WeatherSampleDTO dto = new WeatherSampleRepository().toDto(sample);

        assertThat(dto.getLatitude()).isEqualTo(49.54821);
        assertThat(dto.getLongitude()).isEqualTo(25.59631);
    }
}
