package org.github.tess1o.geopulse.weather.job;

import org.github.tess1o.geopulse.streaming.events.TimelineDataChangedEvent;
import org.github.tess1o.geopulse.weather.event.WeatherSettingsChangedEvent;
import org.github.tess1o.geopulse.weather.service.WeatherBackfillRunResult;
import org.github.tess1o.geopulse.weather.service.WeatherConfigurationService;
import org.github.tess1o.geopulse.weather.service.WeatherReconciliationQueueStatus;
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
class WeatherHistoricalReconciliationJobTest {

    private final WeatherService weatherService = mock(WeatherService.class);
    private final WeatherConfigurationService configurationService = mock(WeatherConfigurationService.class);
    private final DirectExecutorService executorService = new DirectExecutorService();
    private WeatherHistoricalReconciliationJob job;

    @BeforeEach
    void setUp() {
        lenient().when(configurationService.isEnabled()).thenReturn(true);
        lenient().when(configurationService.backfillEnabled()).thenReturn(true);
        lenient().when(configurationService.backfillDiscoveryChunksPerRun()).thenReturn(4);
        lenient().when(weatherService.queueFullHistoricalBackfill())
                .thenReturn(WeatherReconciliationQueueStatus.QUEUED);
        lenient().when(weatherService.queueHistoricalBackfill(any(), any(), any()))
                .thenReturn(WeatherReconciliationQueueStatus.QUEUED);
        lenient().when(weatherService.processPendingHistoricalBackfillChunks(anyInt()))
                .thenReturn(new WeatherBackfillRunResult(0, 0, 0, 0, 0));

        job = new WeatherHistoricalReconciliationJob();
        job.weatherService = weatherService;
        job.configurationService = configurationService;
        job.executorService = executorService;
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    void enablingBackfillQueuesFullReconciliationAndProcessesBoundedChunks() {
        when(weatherService.processPendingHistoricalBackfillChunks(4))
                .thenReturn(new WeatherBackfillRunResult(4, 2, 20, 0, 1));

        job.onWeatherSettingsChanged(new WeatherSettingsChangedEvent(WeatherConfigurationService.BACKFILL_ENABLED));

        verify(weatherService).queueFullHistoricalBackfill();
        verify(weatherService).processPendingHistoricalBackfillChunks(4);
        verify(weatherService).fetchQueuedSamples();
    }

    @Test
    void unrelatedWeatherSettingDoesNotQueueOrProcessBackfill() {
        job.onWeatherSettingsChanged(new WeatherSettingsChangedEvent(WeatherConfigurationService.DAILY_REQUEST_LIMIT));

        verifyNoInteractions(weatherService);
        assertThat(executorService.executionCount()).isZero();
    }

    @Test
    void timelineDataChangedQueuesExactRangeBeforeProcessing() {
        UUID userId = UUID.randomUUID();
        Instant affectedFrom = Instant.parse("2026-06-25T08:00:00Z");
        Instant affectedTo = Instant.parse("2026-06-26T08:00:00Z");
        when(weatherService.processPendingHistoricalBackfillChunks(4))
                .thenReturn(new WeatherBackfillRunResult(1, 2, 0, 0, 0));

        job.onTimelineDataChanged(new TimelineDataChangedEvent(userId, affectedFrom, affectedTo, UUID.randomUUID()));

        var inOrder = inOrder(weatherService);
        inOrder.verify(weatherService).queueHistoricalBackfill(userId, affectedFrom, affectedTo);
        inOrder.verify(weatherService).resetStaleFailedTargetsForRetry();
        inOrder.verify(weatherService).processPendingHistoricalBackfillChunks(4);
        inOrder.verify(weatherService).fetchQueuedSamples();
    }

    @Test
    void knownTargetsDoNotTriggerImmediateFetch() {
        when(weatherService.processPendingHistoricalBackfillChunks(4))
                .thenReturn(new WeatherBackfillRunResult(1, 0, 25, 0, 0));

        job.onStartup(null);

        verify(weatherService).processPendingHistoricalBackfillChunks(4);
        verify(weatherService, never()).fetchQueuedSamples();
    }

    @Test
    void scheduledRunOnlyDrainsPersistedWork() {
        when(configurationService.backfillDiscoveryChunksPerRun()).thenReturn(7);

        job.reconcileHistoricalWeatherTargets();

        verify(weatherService).resetStaleFailedTargetsForRetry();
        verify(weatherService).processPendingHistoricalBackfillChunks(7);
        verify(weatherService, never()).queueFullHistoricalBackfill();
        verify(weatherService, never()).fetchQueuedSamples();
    }

    @Test
    void weatherDisabledSkipsEveryBackfillEntrypointWithoutSubmittingWork() {
        when(configurationService.isEnabled()).thenReturn(false);
        when(weatherService.queueFullHistoricalBackfill())
                .thenReturn(WeatherReconciliationQueueStatus.WEATHER_DISABLED);
        when(weatherService.queueHistoricalBackfill(any(), any(), any()))
                .thenReturn(WeatherReconciliationQueueStatus.WEATHER_DISABLED);
        UUID userId = UUID.randomUUID();
        Instant affectedFrom = Instant.parse("2026-06-25T08:00:00Z");
        Instant affectedTo = Instant.parse("2026-06-26T08:00:00Z");

        job.onStartup(null);
        job.onWeatherSettingsChanged(new WeatherSettingsChangedEvent(WeatherConfigurationService.BACKFILL_ENABLED));
        job.onTimelineDataChanged(new TimelineDataChangedEvent(userId, affectedFrom, affectedTo, null));
        job.reconcileHistoricalWeatherTargets();

        verify(weatherService).queueFullHistoricalBackfill();
        verify(weatherService).queueHistoricalBackfill(userId, affectedFrom, affectedTo);
        verifyNoMoreInteractions(weatherService);
        assertThat(executorService.executionCount()).isZero();
    }

    @Test
    void backfillDisabledSkipsEveryBackfillEntrypointWithoutSubmittingWork() {
        when(configurationService.backfillEnabled()).thenReturn(false);
        when(weatherService.queueFullHistoricalBackfill())
                .thenReturn(WeatherReconciliationQueueStatus.BACKFILL_DISABLED);
        when(weatherService.queueHistoricalBackfill(any(), any(), any()))
                .thenReturn(WeatherReconciliationQueueStatus.BACKFILL_DISABLED);
        UUID userId = UUID.randomUUID();
        Instant affectedFrom = Instant.parse("2026-06-25T08:00:00Z");
        Instant affectedTo = Instant.parse("2026-06-26T08:00:00Z");

        job.onStartup(null);
        job.onWeatherSettingsChanged(new WeatherSettingsChangedEvent(WeatherConfigurationService.BACKFILL_ENABLED));
        job.onTimelineDataChanged(new TimelineDataChangedEvent(userId, affectedFrom, affectedTo, null));
        job.reconcileHistoricalWeatherTargets();

        verify(weatherService).queueFullHistoricalBackfill();
        verify(weatherService).queueHistoricalBackfill(userId, affectedFrom, affectedTo);
        verifyNoMoreInteractions(weatherService);
        assertThat(executorService.executionCount()).isZero();
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
