package org.github.tess1o.geopulse.weather.service;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.prometheus.GeoPulseWorkloadMetrics;
import org.github.tess1o.geopulse.streaming.events.TimelineDataChangedEvent;
import org.github.tess1o.geopulse.weather.dto.WeatherProcessingStatus;
import org.github.tess1o.geopulse.weather.dto.WeatherWorkAcceptedResponse;
import org.github.tess1o.geopulse.weather.event.WeatherSettingsChangedEvent;
import org.github.tess1o.geopulse.weather.repository.WeatherBackfillReconciliationRepository;
import org.github.tess1o.geopulse.weather.repository.WeatherSampleTargetRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The single in-process weather execution loop. Database queues provide durability; this
 * worker only provides immediate, coalesced wake-ups for the supported single-instance mode.
 */
@ApplicationScoped
@Slf4j
public class WeatherPipelineWorker {

    private static final Set<String> COVERAGE_SETTINGS = Set.of(
            WeatherConfigurationService.WEATHER_ENABLED,
            WeatherConfigurationService.BACKFILL_ENABLED,
            WeatherConfigurationService.COORDINATE_PRECISION
    );

    @Inject
    WeatherService weatherService;

    @Inject
    WeatherConfigurationService configurationService;

    @Inject
    WeatherSampleTargetRepository targetRepository;

    @Inject
    WeatherBackfillReconciliationRepository reconciliationRepository;

    @Inject
    @Identifier("weather-processing")
    ExecutorService executor;

    @Inject
    GeoPulseWorkloadMetrics workloadMetrics;

    @ConfigProperty(name = "geopulse.weather.targets.in-progress-timeout-minutes", defaultValue = "60")
    int inProgressTimeoutMinutes;

    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean rerunRequested = new AtomicBoolean();
    private volatile String phase = "IDLE";
    private volatile String trigger;
    private volatile String lastBlockReason;
    private volatile Instant startedAt;
    private volatile Instant lastCompletedAt;
    private volatile Instant nextOngoingDiscoveryAt = Instant.EPOCH;

    void onStartup(@Observes StartupEvent ignored) {
        wake("startup");
    }

    void onTimelineChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) TimelineDataChangedEvent event) {
        submitEventHandling("timeline changed", () -> handleTimelineChanged(event));
    }

    private void handleTimelineChanged(TimelineDataChangedEvent event) {
        WeatherReconciliationQueueStatus queued = weatherService.queueHistoricalBackfill(
                event.getUserId(), event.getAffectedFrom(), event.getAffectedTo());
        if (queued == WeatherReconciliationQueueStatus.QUEUED) {
            wake("timeline changed");
        }
    }

    void onSettingsChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) WeatherSettingsChangedEvent event) {
        submitEventHandling("weather setting changed: " + event.key(), () -> handleSettingsChanged(event.key()));
    }

    private void handleSettingsChanged(String key) {
        if (COVERAGE_SETTINGS.contains(key)) {
            weatherService.queueFullHistoricalBackfill();
        }
        wake("weather setting changed: " + key);
    }

    private void submitEventHandling(String reason, Runnable handler) {
        try {
            executor.submit(() -> {
                try {
                    handler.run();
                } catch (RuntimeException e) {
                    phase = "BLOCKED";
                    lastBlockReason = reason + " handler failed: " + e.getMessage();
                    log.error(lastBlockReason, e);
                }
            });
        } catch (RuntimeException e) {
            phase = "BLOCKED";
            lastBlockReason = "Unable to submit " + reason + " handler: " + e.getMessage();
            log.error(lastBlockReason, e);
        }
    }

    @Scheduled(every = "1m", delayed = "10s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void watchdog() {
        long started = metricsStart();
        boolean due = hasDueWork();
        if (due) {
            wake("watchdog");
        } else {
            log.debug("Weather watchdog found no due work");
        }
        recordWatchdog(started, due ? "wake" : "idle");
    }

    public WeatherWorkAcceptedResponse wake(String reason) {
        rerunRequested.set(true);
        boolean submitted = running.compareAndSet(false, true);
        if (submitted) {
            submitDrain(reason == null ? "unspecified" : reason);
        }
        return WeatherWorkAcceptedResponse.builder()
                .accepted(true)
                .alreadyRunning(!submitted)
                .queuedUserRanges((int) Math.min(Integer.MAX_VALUE, reconciliationRepository.countPendingUserRanges()))
                .message(submitted ? "The weather worker was notified" : "Weather processing is already running; work was coalesced")
                .build();
    }

    public WeatherProcessingStatus snapshot() {
        return WeatherProcessingStatus.builder()
                .running(running.get())
                .waitingWorkers(rerunRequested.get() && running.get() ? 1 : 0)
                .phase(phase)
                .trigger(trigger)
                .reason(lastBlockReason)
                .startedAt(startedAt)
                .build();
    }

    public Instant lastCompletedAt() {
        return lastCompletedAt;
    }

    public String lastBlockReason() {
        return lastBlockReason;
    }

    public boolean isRunning() {
        return running.get();
    }

    private void submitDrain(String reason) {
        try {
            executor.submit(() -> drain(reason));
        } catch (RuntimeException e) {
            running.set(false);
            phase = "BLOCKED";
            lastBlockReason = "Unable to submit weather worker: " + e.getMessage();
            log.error(lastBlockReason, e);
        }
    }

    private void drain(String reason) {
        long runStarted = metricsStart();
        startedAt = Instant.now();
        trigger = reason;
        lastBlockReason = null;
        int fetched = 0;
        int chunks = 0;
        String result = "success";
        log.info("Weather pipeline started: trigger={}", reason);
        try {
            weatherService.resetRecoverableTargetsForRetry();
            while (true) {
                rerunRequested.set(false);
                discoverOngoingIfDue();

                phase = "FETCHING";
                WeatherFetchBatchResult fetch = weatherService.fetchNextQueuedSampleGroup();
                fetched += fetch.processedTargets();
                if (fetch.blockedReason() != null) {
                    phase = "BLOCKED";
                    lastBlockReason = fetch.blockedReason();
                    result = "blocked";
                    break;
                }
                if (fetch.workClaimed()) {
                    continue;
                }

                phase = "DISCOVERING";
                WeatherBackfillRunResult discovery = weatherService.processPendingHistoricalBackfillChunks(1);
                chunks += discovery.chunksProcessed();
                if (discovery.chunksProcessed() > 0) {
                    continue;
                }
                if (rerunRequested.getAndSet(false)) {
                    continue;
                }
                phase = "IDLE";
                break;
            }
        } catch (Exception e) {
            result = "error";
            phase = "BLOCKED";
            lastBlockReason = e.getMessage();
            log.error("Weather pipeline failed on {}: {}", reason, e.getMessage(), e);
        } finally {
            lastCompletedAt = Instant.now();
            recordWorker(runStarted, result);
            recordQueueDepth();
            log.info("Weather pipeline completed: trigger={}, result={}, fetchedTargets={}, discoveryChunks={}, blockReason={}",
                    reason, result, fetched, chunks, lastBlockReason);
            running.set(false);
            if (rerunRequested.getAndSet(false) && running.compareAndSet(false, true)) {
                submitDrain("coalesced wake");
            }
        }
    }

    private void discoverOngoingIfDue() {
        Instant now = Instant.now();
        if (!configurationService.isEnabled() || !configurationService.ongoingEnabled()
                || now.isBefore(nextOngoingDiscoveryAt)) {
            return;
        }
        phase = "DISCOVERING_ONGOING";
        weatherService.discoverOngoingTargets();
        nextOngoingDiscoveryAt = now.plusSeconds(configurationService.ongoingIntervalMinutes() * 60L);
    }

    private boolean hasDueWork() {
        if (!configurationService.isEnabled()) {
            return false;
        }
        Instant now = Instant.now();
        Instant staleClaimCutoff = now.minus(Duration.ofMinutes(Math.max(1, inProgressTimeoutMinutes)));
        if (targetRepository.hasStaleInProgressTargets(staleClaimCutoff)) {
            return true;
        }
        if (configurationService.ongoingEnabled() && !now.isBefore(nextOngoingDiscoveryAt)) {
            return true;
        }
        if (targetRepository.hasPendingTargets()) {
            return targetRepository.countClaimablePendingTargets(now) > 0
                    && weatherService.providersAvailableForFetch(now);
        }
        if (configurationService.backfillEnabled() && reconciliationRepository.hasPendingRanges()) {
            return true;
        }
        return false;
    }

    private long metricsStart() {
        return workloadMetrics == null ? System.nanoTime() : workloadMetrics.start();
    }

    private void recordWorker(long started, String result) {
        if (workloadMetrics != null) {
            workloadMetrics.recordTimer("geopulse.weather.worker.duration", started,
                    "component", "weather", "result", result);
        }
    }

    private void recordWatchdog(long started, String result) {
        if (workloadMetrics != null) {
            workloadMetrics.recordTimer("geopulse.weather.watchdog.duration", started,
                    "component", "weather", "result", result);
        }
    }

    private void recordQueueDepth() {
        if (workloadMetrics == null) {
            return;
        }
        try {
            for (Map.Entry<String, Long> entry : targetRepository.countByStatus().entrySet()) {
                workloadMetrics.setGauge("geopulse.weather.queue.depth", entry.getValue(),
                        "queue", "targets", "status", entry.getKey());
            }
            workloadMetrics.setGauge("geopulse.weather.queue.depth",
                    reconciliationRepository.countPendingUserRanges(),
                    "queue", "dirty_ranges", "status", "PENDING");
        } catch (RuntimeException e) {
            log.debug("Unable to update weather queue-depth metrics", e);
        }
    }
}
