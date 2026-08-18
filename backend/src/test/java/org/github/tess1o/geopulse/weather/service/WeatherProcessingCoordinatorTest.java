package org.github.tess1o.geopulse.weather.service;

import org.github.tess1o.geopulse.weather.dto.WeatherRunSummary;
import org.github.tess1o.geopulse.weather.dto.WeatherStatusResponse;
import org.github.tess1o.geopulse.weather.dto.WeatherTargetQueueResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class WeatherProcessingCoordinatorTest {

    private final WeatherService weatherService = mock(WeatherService.class);
    private final WeatherConfigurationService configurationService = mock(WeatherConfigurationService.class);
    private WeatherProcessingCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new WeatherProcessingCoordinator();
        coordinator.weatherService = weatherService;
        coordinator.configurationService = configurationService;

        lenient().when(configurationService.isEnabled()).thenReturn(true);
        lenient().when(configurationService.backfillEnabled()).thenReturn(true);
        lenient().when(weatherService.status()).thenReturn(WeatherStatusResponse.builder().build());
    }

    @Test
    void importRangeDiscoversAndFetchesBeforeHistoricalBacklog() {
        UUID userId = UUID.randomUUID();
        Instant start = Instant.parse("2026-08-14T09:00:00Z");
        Instant end = Instant.parse("2026-08-17T22:00:00Z");
        when(weatherService.discoverImportBackfillTargets(userId, start, end))
                .thenReturn(WeatherTargetQueueResponse.builder()
                        .targetsCreated(8)
                        .targetsAlreadyKnown(2)
                        .targetsSkipped(0)
                        .build());
        when(weatherService.fetchQueuedSamples()).thenReturn(8);

        WeatherRunSummary summary = coordinator.processImportRange(userId, start, end, "import test");

        assertThat(summary.getResult()).isEqualTo("success");
        assertThat(summary.getSource()).isEqualTo("IMPORT_BACKFILL");
        assertThat(summary.getTargetsCreated()).isEqualTo(8);
        assertThat(summary.getFetchedTargets()).isEqualTo(8);

        var inOrder = inOrder(weatherService);
        inOrder.verify(weatherService).discoverImportBackfillTargets(userId, start, end);
        inOrder.verify(weatherService).fetchQueuedSamples();
        verify(weatherService, never()).processPendingHistoricalBackfillChunks(anyInt());
    }
}
