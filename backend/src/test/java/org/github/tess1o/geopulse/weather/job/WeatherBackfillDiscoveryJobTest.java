package org.github.tess1o.geopulse.weather.job;

import org.github.tess1o.geopulse.streaming.events.TimelineDataChangedEvent;
import org.github.tess1o.geopulse.weather.dto.WeatherTargetQueueResponse;
import org.github.tess1o.geopulse.weather.service.WeatherService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

@Tag("unit")
class WeatherBackfillDiscoveryJobTest {

    private final WeatherService weatherService = mock(WeatherService.class);
    private final ExecutorService executorService = new DirectExecutorService();
    private WeatherBackfillDiscoveryJob job;

    @BeforeEach
    void setUp() {
        job = new WeatherBackfillDiscoveryJob();
        job.weatherService = weatherService;
        job.executorService = executorService;
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
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
            command.run();
        }
    }
}
