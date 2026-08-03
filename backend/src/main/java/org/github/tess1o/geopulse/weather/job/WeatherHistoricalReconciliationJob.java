package org.github.tess1o.geopulse.weather.job;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.streaming.events.TimelineDataChangedEvent;
import org.github.tess1o.geopulse.weather.event.WeatherSettingsChangedEvent;
import org.github.tess1o.geopulse.weather.service.WeatherBackfillRunResult;
import org.github.tess1o.geopulse.weather.service.WeatherConfigurationService;
import org.github.tess1o.geopulse.weather.service.WeatherService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestrates durable historical weather reconciliation.
 *
 * <p>Timeline and relevant settings events first persist affected ranges in
 * {@code weather_backfill_reconciliations}, then ask this job to drain a bounded number of
 * 90-day chunks. Startup and scheduled executions only resume persisted work; they do not
 * scan the complete timeline. Overlapping triggers are coalesced both in memory and in the
 * database. Event-driven runs may also start the independent sample fetch queue after new
 * targets are found.</p>
 *
 * <p>See {@code docs/WEATHER_PROCESSING.md} for the complete job and data flow.</p>
 */
@ApplicationScoped
@Slf4j
public class WeatherHistoricalReconciliationJob {

    @Inject
    WeatherService weatherService;

    @Inject
    WeatherConfigurationService configurationService;

    @Inject
    @Identifier("weather-processing")
    ExecutorService executorService;

    @ConfigProperty(name = "geopulse.weather.backfill.discovery.chunks-per-run", defaultValue = "4")
    int chunksPerRun = 4;

    private final AtomicBoolean processingRunning = new AtomicBoolean(false);
    private final AtomicBoolean processingPending = new AtomicBoolean(false);

    void onStartup(@Observes StartupEvent ignored) {
        if (!shouldRunBackfillDiscovery("startup")) {
            return;
        }

        submitProcessing("startup", true);
    }

    void onWeatherSettingsChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) WeatherSettingsChangedEvent event) {
        String reason = "weather setting changed: " + event.key();
        if (!shouldRunBackfillDiscovery(reason)) {
            return;
        }
        if (!WeatherConfigurationService.WEATHER_ENABLED.equals(event.key())
                && !WeatherConfigurationService.BACKFILL_ENABLED.equals(event.key())
                && !WeatherConfigurationService.COORDINATE_PRECISION.equals(event.key())) {
            log.debug("Weather historical reconciliation ignored {}: setting does not change backfill coverage", reason);
            return;
        }

        weatherService.queueFullHistoricalBackfill();
        submitProcessing(reason, true);
    }

    void onTimelineDataChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) TimelineDataChangedEvent event) {
        String reason = "timeline data changed for user " + event.getUserId()
                + " from " + event.getAffectedFrom()
                + " to " + event.getAffectedTo();
        if (!shouldRunBackfillDiscovery(reason)) {
            return;
        }

        weatherService.queueHistoricalBackfill(
                event.getUserId(),
                event.getAffectedFrom(),
                event.getAffectedTo()
        );
        submitProcessing(reason, true);
    }

    @RunOnVirtualThread
    @Scheduled(every = "${geopulse.weather.backfill.discovery.job.interval:30m}",
            delayed = "${geopulse.weather.backfill.discovery.job.delay:5m}")
    public void reconcileHistoricalWeatherTargets() {
        if (!shouldRunBackfillDiscovery("scheduled")) {
            return;
        }

        runProcessing("scheduled", false);
    }

    private void submitProcessing(String reason, boolean fetchAfterDiscovery) {
        try {
            CompletableFuture.runAsync(() -> {
                if (!shouldRunBackfillDiscovery(reason)) {
                    return;
                }
                runProcessing(reason, fetchAfterDiscovery);
            }, executorService).exceptionally(throwable -> {
                processingPending.set(true);
                log.error("Weather historical reconciliation failed on {}: {}", reason, throwable.getMessage(), throwable);
                return null;
            });
        } catch (RuntimeException e) {
            processingPending.set(true);
            log.error("Failed to submit weather historical reconciliation on {}: {}", reason, e.getMessage(), e);
        }
    }

    private void runProcessing(String reason, boolean fetchAfterDiscovery) {
        if (!shouldRunBackfillDiscovery(reason)) {
            return;
        }
        if (!processingRunning.compareAndSet(false, true)) {
            processingPending.set(true);
            log.info("Weather historical reconciliation is already running; queued another drain after {}", reason);
            return;
        }

        processingPending.set(false);
        long startedAtNanos = System.nanoTime();
        try {
            log.info("Weather historical reconciliation starting on {}", reason);
            long resetFailedTargets = weatherService.resetStaleFailedTargetsForRetry();
            WeatherBackfillRunResult result = weatherService.processPendingHistoricalBackfillChunks(chunksPerRun);
            log.info("Weather historical reconciliation completed on {}: durationMs={}, chunks={}, created={}, known={}, "
                            + "skipped={}, pendingRanges={}",
                    reason,
                    elapsedMillis(startedAtNanos),
                    result.chunksProcessed(),
                    result.targetsCreated(),
                    result.targetsAlreadyKnown(),
                    result.targetsSkipped(),
                    result.pendingRanges());

            if (fetchAfterDiscovery && (result.targetsCreated() > 0 || resetFailedTargets > 0)) {
                int fetched = weatherService.fetchQueuedSamples();
                log.info("Weather historical reconciliation fetched {} queued samples after {}", fetched, reason);
            }
        } catch (Exception e) {
            log.error("Weather historical reconciliation failed on {} after durationMs={}: {}",
                    reason, elapsedMillis(startedAtNanos), e.getMessage(), e);
        } finally {
            processingRunning.set(false);
            if (processingPending.compareAndSet(true, false)) {
                submitProcessing("queued reconciliation after " + reason, true);
            }
        }
    }

    private boolean shouldRunBackfillDiscovery(String reason) {
        if (!configurationService.isEnabled()) {
            log.info("Weather historical reconciliation skipped on {}: weather is disabled", reason);
            return false;
        }
        if (!configurationService.backfillEnabled()) {
            log.info("Weather historical reconciliation skipped on {}: weather backfill is disabled", reason);
            return false;
        }
        return true;
    }

    private long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }
}
