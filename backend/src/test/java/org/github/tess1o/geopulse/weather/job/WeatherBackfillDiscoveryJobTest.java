package org.github.tess1o.geopulse.weather.job;

import org.github.tess1o.geopulse.streaming.events.TimelineDataChangedEvent;
import org.github.tess1o.geopulse.weather.dto.WeatherTargetQueueResponse;
import org.github.tess1o.geopulse.weather.event.WeatherSettingsChangedEvent;
import org.github.tess1o.geopulse.weather.service.WeatherConfigurationService;
import org.github.tess1o.geopulse.weather.service.WeatherService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class WeatherBackfillDiscoveryJobTest {

    private final WeatherService weatherService = mock(WeatherService.class);
    private final WeatherConfigurationService configurationService = mock(WeatherConfigurationService.class);
    private final DirectExecutorService executorService = new DirectExecutorService();
    private WeatherBackfillDiscoveryJob job;

    @BeforeEach
    void setUp() {
        lenient().when(configurationService.isEnabled()).thenReturn(true);
        lenient().when(configurationService.backfillEnabled()).thenReturn(true);

        job = new WeatherBackfillDiscoveryJob();
        job.weatherService = weatherService;
        job.configurationService = configurationService;
        job.executorService = executorService;
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    void weatherSettingsChangedDiscoversAndFetchesQueuedSamples() {
        when(weatherService.discoverHistoricalBackfillTargets())
                .thenReturn(WeatherTargetQueueResponse.builder().targetsCreated(2).build());

        job.onWeatherSettingsChanged(new WeatherSettingsChangedEvent("weather.backfill.enabled"));

        verify(weatherService).resetStaleFailedTargetsForRetry();
        verify(weatherService).discoverHistoricalBackfillTargets();
        verify(weatherService).fetchQueuedSamples();
    }

    @Test
    void weatherDisabledSkipsEveryBackfillEntrypointWithoutSubmittingWork() {
        when(configurationService.isEnabled()).thenReturn(false);
        UUID userId = UUID.randomUUID();
        Instant affectedFrom = Instant.parse("2026-06-25T08:00:00Z");
        Instant affectedTo = Instant.parse("2026-06-26T08:00:00Z");

        job.onStartup(null);
        job.onWeatherSettingsChanged(new WeatherSettingsChangedEvent("weather.backfill.enabled"));
        job.onTimelineDataChanged(new TimelineDataChangedEvent(userId, affectedFrom, affectedTo, null));
        job.discoverHistoricalWeatherTargets();

        verifyNoInteractions(weatherService);
        assertThat(executorService.executionCount()).isZero();
    }

    @Test
    void backfillDisabledSkipsEveryBackfillEntrypointWithoutSubmittingWork() {
        when(configurationService.backfillEnabled()).thenReturn(false);
        UUID userId = UUID.randomUUID();
        Instant affectedFrom = Instant.parse("2026-06-25T08:00:00Z");
        Instant affectedTo = Instant.parse("2026-06-26T08:00:00Z");

        job.onStartup(null);
        job.onWeatherSettingsChanged(new WeatherSettingsChangedEvent("weather.backfill.enabled"));
        job.onTimelineDataChanged(new TimelineDataChangedEvent(userId, affectedFrom, affectedTo, null));
        job.discoverHistoricalWeatherTargets();

        verifyNoInteractions(weatherService);
        assertThat(executorService.executionCount()).isZero();
    }

    @Test
    void weatherSettingsChangedQueuesFullKickstartWhenDiscoveryIsRunning() {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant affectedFrom = Instant.parse("2026-06-25T08:00:00Z");
        Instant affectedTo = Instant.parse("2026-06-26T08:00:00Z");
        when(weatherService.discoverHistoricalBackfillTargets(userId, affectedFrom, affectedTo))
                .thenAnswer(invocation -> {
                    job.onWeatherSettingsChanged(new WeatherSettingsChangedEvent("weather.backfill.enabled"));
                    return WeatherTargetQueueResponse.builder().targetsSkipped(1).build();
                });
        when(weatherService.discoverHistoricalBackfillTargets())
                .thenReturn(WeatherTargetQueueResponse.builder().targetsCreated(3).build());

        job.onTimelineDataChanged(new TimelineDataChangedEvent(userId, affectedFrom, affectedTo, jobId));

        verify(weatherService).discoverHistoricalBackfillTargets(userId, affectedFrom, affectedTo);
        verify(weatherService).discoverHistoricalBackfillTargets();
        verify(weatherService).fetchQueuedSamples();
    }

    @Test
    void timelineDataChangedDiscoversChangedRangeAndFetchesQueuedSamples() {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant affectedFrom = Instant.parse("2026-06-25T08:00:00Z");
        Instant affectedTo = Instant.parse("2026-06-26T08:00:00Z");
        when(weatherService.discoverHistoricalBackfillTargets(userId, affectedFrom, affectedTo))
                .thenReturn(WeatherTargetQueueResponse.builder().targetsCreated(2).build());

        job.onTimelineDataChanged(new TimelineDataChangedEvent(userId, affectedFrom, affectedTo, jobId));

        verify(weatherService).resetStaleFailedTargetsForRetry();
        verify(weatherService).discoverHistoricalBackfillTargets(userId, affectedFrom, affectedTo);
        verify(weatherService).fetchQueuedSamples();
    }

    @Test
    void timelineDataChangedDoesNotFetchWhenNothingWasDiscoveredOrReset() {
        UUID userId = UUID.randomUUID();
        Instant affectedFrom = Instant.parse("2026-06-25T08:00:00Z");
        Instant affectedTo = Instant.parse("2026-06-26T08:00:00Z");
        when(weatherService.discoverHistoricalBackfillTargets(userId, affectedFrom, affectedTo))
                .thenReturn(WeatherTargetQueueResponse.builder().targetsSkipped(1).build());

        job.onTimelineDataChanged(new TimelineDataChangedEvent(userId, affectedFrom, affectedTo, null));

        verify(weatherService).resetStaleFailedTargetsForRetry();
        verify(weatherService).discoverHistoricalBackfillTargets(userId, affectedFrom, affectedTo);
        verify(weatherService, never()).fetchQueuedSamples();
    }

    @Test
    void timelineDataChangedDoesNotFetchWhenOnlyKnownTargetsWereDiscovered() {
        UUID userId = UUID.randomUUID();
        Instant affectedFrom = Instant.parse("2026-06-25T08:00:00Z");
        Instant affectedTo = Instant.parse("2026-06-26T08:00:00Z");
        when(weatherService.discoverHistoricalBackfillTargets(userId, affectedFrom, affectedTo))
                .thenReturn(WeatherTargetQueueResponse.builder().targetsAlreadyKnown(2).build());

        job.onTimelineDataChanged(new TimelineDataChangedEvent(userId, affectedFrom, affectedTo, null));

        verify(weatherService).resetStaleFailedTargetsForRetry();
        verify(weatherService).discoverHistoricalBackfillTargets(userId, affectedFrom, affectedTo);
        verify(weatherService, never()).fetchQueuedSamples();
    }

    @Test
    void scheduledDiscoveryResetsStaleFailedTargetsButDoesNotFetchImmediately() {
        when(weatherService.resetStaleFailedTargetsForRetry()).thenReturn(3L);
        when(weatherService.discoverHistoricalBackfillTargets())
                .thenReturn(WeatherTargetQueueResponse.builder().targetsSkipped(1).build());

        job.discoverHistoricalWeatherTargets();

        verify(weatherService).resetStaleFailedTargetsForRetry();
        verify(weatherService).discoverHistoricalBackfillTargets();
        verify(weatherService, never()).fetchQueuedSamples();
    }

    private static class DirectExecutorService extends AbstractExecutorService {
        private boolean shutdown;
        private int executionCount;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            executionCount++;
            command.run();
        }

        int executionCount() {
            return executionCount;
        }
    }
}
