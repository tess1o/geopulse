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
import org.github.tess1o.geopulse.streaming.events.TimelineDataChangedEvent;
import org.github.tess1o.geopulse.prometheus.GeoPulseWorkloadMetrics;
import org.github.tess1o.geopulse.weather.event.WeatherSettingsChangedEvent;
import org.github.tess1o.geopulse.weather.service.WeatherBackfillRunResult;
import org.github.tess1o.geopulse.weather.service.WeatherConfigurationService;
import org.github.tess1o.geopulse.weather.dto.WeatherRunSummary;
import org.github.tess1o.geopulse.weather.service.WeatherProcessingCoordinator;
import org.github.tess1o.geopulse.weather.service.WeatherReconciliationQueueStatus;
import org.github.tess1o.geopulse.weather.service.WeatherService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
    WeatherProcessingCoordinator weatherProcessingCoordinator;

    @Inject
    @Identifier("weather-processing")
    ExecutorService executorService;

    @Inject
    GeoPulseWorkloadMetrics workloadMetrics;

    private final AtomicBoolean processingRunning = new AtomicBoolean(false);
    private final AtomicBoolean processingPending = new AtomicBoolean(false);
    private final AtomicInteger submittedProcessingTasks = new AtomicInteger();

    void onStartup(@Observes StartupEvent ignored) {
        if (!shouldRunBackfillDiscovery("startup")) {
            return;
        }

        submitProcessing("startup", true);
    }

    void onWeatherSettingsChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) WeatherSettingsChangedEvent event) {
        String reason = "weather setting changed: " + event.key();
        if (!WeatherConfigurationService.WEATHER_ENABLED.equals(event.key())
                && !WeatherConfigurationService.BACKFILL_ENABLED.equals(event.key())
                && !WeatherConfigurationService.COORDINATE_PRECISION.equals(event.key())) {
            log.debug("Weather historical reconciliation ignored {}: setting does not change backfill coverage", reason);
            return;
        }

        WeatherReconciliationQueueStatus status = weatherService.queueFullHistoricalBackfill();
        if (!handleQueueStatus(reason, status)) {
            return;
        }
        submitProcessing(reason, true);
    }

    void onTimelineDataChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) TimelineDataChangedEvent event) {
        String reason = "timeline data changed for user " + event.getUserId()
                + " from " + event.getAffectedFrom()
                + " to " + event.getAffectedTo();
        WeatherReconciliationQueueStatus status = weatherService.queueHistoricalBackfill(
                event.getUserId(),
                event.getAffectedFrom(),
                event.getAffectedTo()
        );
        if (!handleQueueStatus(reason, status)) {
            return;
        }
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
        submittedProcessingTasks.incrementAndGet();
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
            }).whenComplete((ignored, throwable) -> submittedProcessingTasks.decrementAndGet());
        } catch (RuntimeException e) {
            submittedProcessingTasks.decrementAndGet();
            processingPending.set(true);
            log.error("Failed to submit weather historical reconciliation on {}: {}", reason, e.getMessage(), e);
        }
    }

    private void runProcessing(String reason, boolean fetchAfterDiscovery) {
        if (!shouldRunBackfillDiscovery(reason)) {
            return;
        }
        long jobStart = metricsStart();
        String resultTag = "success";
        if (!processingRunning.compareAndSet(false, true)) {
            processingPending.set(true);
            log.info("Weather historical reconciliation is already running; queued another drain after {}", reason);
            recordWeatherJob(jobStart, "historical_reconciliation", triggerTag(reason), "queued");
            return;
        }

        processingPending.set(false);
        long startedAtNanos = System.nanoTime();
        try {
            log.info("Weather historical reconciliation starting on {}", reason);
            WeatherRunSummary result = processHistorical(reason, fetchAfterDiscovery);
            log.info("Weather historical reconciliation completed on {}: durationMs={}, chunks={}, created={}, known={}, "
                            + "skipped={}, pendingUserRanges={}",
                    reason,
                    elapsedMillis(startedAtNanos),
                    result.getChunksProcessed(),
                    result.getTargetsCreated(),
                    result.getTargetsAlreadyKnown(),
                    result.getTargetsSkipped(),
                    result.getPendingUserRanges());

            if (result.getFetchedTargets() > 0) {
                log.info("Weather historical reconciliation fetched {} queued samples after {}",
                        result.getFetchedTargets(), reason);
            }
        } catch (Exception e) {
            resultTag = "error";
            log.error("Weather historical reconciliation failed on {} after durationMs={}: {}",
                    reason, elapsedMillis(startedAtNanos), e.getMessage(), e);
        } finally {
            recordWeatherJob(jobStart, "historical_reconciliation", triggerTag(reason), resultTag);
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

    private boolean handleQueueStatus(String reason, WeatherReconciliationQueueStatus status) {
        return switch (status) {
            case QUEUED -> true;
            case WEATHER_DISABLED -> {
                log.info("Weather historical reconciliation skipped on {}: weather is disabled", reason);
                yield false;
            }
            case BACKFILL_DISABLED -> {
                log.info("Weather historical reconciliation skipped on {}: weather backfill is disabled", reason);
                yield false;
            }
            case INVALID_RANGE -> {
                log.warn("Weather historical reconciliation skipped on {}: affected range is invalid", reason);
                yield false;
            }
        };
    }

    private long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    boolean hasSubmittedProcessingTasks() {
        return submittedProcessingTasks.get() > 0;
    }

    private WeatherRunSummary processHistorical(String reason, boolean fetchAfterDiscovery) {
        if (weatherProcessingCoordinator != null) {
            return weatherProcessingCoordinator.processHistorical(
                    reason,
                    fetchAfterDiscovery,
                    configurationService.backfillDiscoveryChunksPerRun());
        }

        long resetFailedTargets = weatherService.resetStaleFailedTargetsForRetry();
        WeatherBackfillRunResult result = weatherService.processPendingHistoricalBackfillChunks(
                configurationService.backfillDiscoveryChunksPerRun());
        int fetchedTargets = 0;
        if (fetchAfterDiscovery && (result.targetsCreated() > 0 || resetFailedTargets > 0)) {
            fetchedTargets = weatherService.fetchQueuedSamples();
        }
        return WeatherRunSummary.builder()
                .chunksProcessed(result.chunksProcessed())
                .targetsCreated(result.targetsCreated())
                .targetsAlreadyKnown(result.targetsAlreadyKnown())
                .targetsSkipped(result.targetsSkipped())
                .pendingUserRanges(result.pendingUserRanges())
                .fetchedTargets(fetchedTargets)
                .build();
    }

    private long metricsStart() {
        return workloadMetrics == null ? System.nanoTime() : workloadMetrics.start();
    }

    private void recordWeatherJob(long startedAtNanos, String job, String trigger, String result) {
        if (workloadMetrics == null) {
            return;
        }
        workloadMetrics.recordTimer("geopulse.weather.job.duration", startedAtNanos,
                "component", "weather",
                "job", job,
                "trigger", trigger,
                "result", result);
    }

    private String triggerTag(String reason) {
        if (reason == null) {
            return "unknown";
        }
        if (reason.startsWith("timeline data changed")) {
            return "timeline_event";
        }
        if (reason.startsWith("weather setting changed")) {
            return "settings";
        }
        if (reason.startsWith("startup")) {
            return "startup";
        }
        if (reason.startsWith("scheduled")) {
            return "scheduled";
        }
        return "internal";
    }
}
