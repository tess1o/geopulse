package org.github.tess1o.geopulse.weather.service;

import org.github.tess1o.geopulse.weather.dto.WeatherWorkAcceptedResponse;
import org.github.tess1o.geopulse.streaming.events.TimelineDataChangedEvent;
import org.github.tess1o.geopulse.prometheus.GeoPulseWorkloadMetrics;
import org.github.tess1o.geopulse.weather.event.WeatherSettingsChangedEvent;
import org.github.tess1o.geopulse.weather.repository.WeatherBackfillReconciliationRepository;
import org.github.tess1o.geopulse.weather.repository.WeatherSampleTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class WeatherPipelineWorkerTest {

    private final WeatherService weatherService = mock(WeatherService.class);
    private final WeatherConfigurationService configurationService = mock(WeatherConfigurationService.class);
    private final WeatherSampleTargetRepository targetRepository = mock(WeatherSampleTargetRepository.class);
    private final WeatherBackfillReconciliationRepository reconciliationRepository = mock(WeatherBackfillReconciliationRepository.class);
    private final GeoPulseWorkloadMetrics workloadMetrics = mock(GeoPulseWorkloadMetrics.class);
    private final QueuedExecutor executor = new QueuedExecutor();
    private WeatherPipelineWorker worker;

    @BeforeEach
    void setUp() {
        worker = new WeatherPipelineWorker();
        worker.weatherService = weatherService;
        worker.configurationService = configurationService;
        worker.targetRepository = targetRepository;
        worker.reconciliationRepository = reconciliationRepository;
        worker.executor = executor;
        worker.workloadMetrics = workloadMetrics;
        worker.inProgressTimeoutMinutes = 60;

        when(configurationService.isEnabled()).thenReturn(true);
        when(configurationService.ongoingEnabled()).thenReturn(false);
        when(weatherService.fetchNextQueuedSampleGroup()).thenReturn(WeatherFetchBatchResult.empty());
        when(weatherService.processPendingHistoricalBackfillChunks(1))
                .thenReturn(new WeatherBackfillRunResult(0, 0, 0, 0, 0));
    }

    @Test
    void coalescesManyWakeupsIntoOneSubmittedWorker() {
        WeatherWorkAcceptedResponse first = worker.wake("first");
        for (int i = 0; i < 100; i++) {
            assertThat(worker.wake("event " + i).isAlreadyRunning()).isTrue();
        }

        assertThat(first.isAlreadyRunning()).isFalse();
        assertThat(executor.queuedTasks()).isOne();

        executor.runNext();

        assertThat(worker.isRunning()).isFalse();
        verify(weatherService, times(1)).resetRecoverableTargetsForRetry();
        verify(weatherService, atLeastOnce()).fetchNextQueuedSampleGroup();
        verify(workloadMetrics).recordTimer(eq("geopulse.weather.worker.duration"), anyLong(),
                eq("component"), eq("weather"), eq("result"), eq("success"));
        verify(workloadMetrics).setGauge("geopulse.weather.queue.depth", 0,
                "queue", "dirty_ranges", "status", "PENDING");
    }

    @Test
    void watchdogWakesWorkerToRecoverStaleInProgressTargets() {
        when(targetRepository.hasStaleInProgressTargets(any(Instant.class))).thenReturn(true);

        worker.watchdog();

        assertThat(executor.queuedTasks()).isOne();
        executor.runNext();
        verify(weatherService).resetRecoverableTargetsForRetry();
        verify(workloadMetrics).recordTimer(eq("geopulse.weather.watchdog.duration"), anyLong(),
                eq("component"), eq("weather"), eq("result"), eq("wake"));
    }

    @Test
    void timelineChangeDefersRangeQueueingUntilSubmittedWorkRuns() {
        UUID userId = UUID.randomUUID();
        Instant from = Instant.parse("2026-08-18T08:00:00Z");
        Instant to = Instant.parse("2026-08-18T09:00:00Z");
        when(weatherService.queueHistoricalBackfill(userId, from, to))
                .thenReturn(WeatherReconciliationQueueStatus.QUEUED);

        worker.onTimelineChanged(new TimelineDataChangedEvent(userId, from, to, null));

        assertThat(executor.queuedTasks()).isOne();
        verify(weatherService, never()).queueHistoricalBackfill(any(), any(), any());

        executor.runNext();

        verify(weatherService).queueHistoricalBackfill(userId, from, to);
        assertThat(executor.queuedTasks()).isOne();
    }

    @Test
    void settingsChangeDefersFullBackfillQueueingUntilSubmittedWorkRuns() {
        worker.onSettingsChanged(new WeatherSettingsChangedEvent(WeatherConfigurationService.WEATHER_ENABLED));

        assertThat(executor.queuedTasks()).isOne();
        verify(weatherService, never()).queueFullHistoricalBackfill();

        executor.runNext();

        verify(weatherService).queueFullHistoricalBackfill();
        assertThat(executor.queuedTasks()).isOne();
    }

    private static class QueuedExecutor extends AbstractExecutorService {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.copyOf(tasks);
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && tasks.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        int queuedTasks() {
            return tasks.size();
        }

        void runNext() {
            tasks.remove().run();
        }
    }
}
