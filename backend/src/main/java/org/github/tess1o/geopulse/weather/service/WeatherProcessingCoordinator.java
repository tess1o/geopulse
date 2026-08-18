package org.github.tess1o.geopulse.weather.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.weather.dto.WeatherProcessingStatus;
import org.github.tess1o.geopulse.weather.dto.WeatherRunSummary;
import org.github.tess1o.geopulse.weather.dto.WeatherStatusResponse;
import org.github.tess1o.geopulse.weather.dto.WeatherTargetQueueResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@ApplicationScoped
@Slf4j
public class WeatherProcessingCoordinator {

    @Inject
    WeatherService weatherService;

    @Inject
    WeatherConfigurationService configurationService;

    private final ReentrantLock processingLock = new ReentrantLock(true);
    private final AtomicInteger waitingWorkers = new AtomicInteger();
    private volatile WeatherProcessingStatus currentProcessing;
    private volatile WeatherRunSummary lastDiscoveryRun;
    private volatile WeatherRunSummary lastFetchRun;

    public WeatherStatusResponse status() {
        WeatherStatusResponse response = weatherService.status();
        response.setProcessing(snapshotProcessing());
        response.setLastDiscoveryRun(lastDiscoveryRun);
        response.setLastFetchRun(lastFetchRun);
        return response;
    }

    public WeatherRunSummary processImportRange(UUID userId, Instant startTime, Instant endTime, String reason) {
        if (!configurationService.isEnabled()) {
            return skippedSummary("import", reason, "IMPORT_BACKFILL", "Weather integration is disabled");
        }
        if (!configurationService.backfillEnabled()) {
            return skippedSummary("import", reason, "IMPORT_BACKFILL", "Historical weather backfill is disabled");
        }
        if (userId == null || startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            return skippedSummary("import", reason, "IMPORT_BACKFILL", "Imported range is not valid for weather enrichment");
        }

        lockInterruptibly("import", reason, userId, startTime, endTime);
        try {
            setCurrent("discovering_import_targets", "import", reason, userId, startTime, endTime);
            Instant startedAt = Instant.now();
            long startedNanos = System.nanoTime();
            WeatherTargetQueueResponse discovery = weatherService.discoverImportBackfillTargets(userId, startTime, endTime);
            WeatherRunSummary discoverySummary = discoverySummary(
                    "import",
                    reason,
                    "IMPORT_BACKFILL",
                    startedAt,
                    startedNanos,
                    discovery,
                    0,
                    null
            );
            lastDiscoveryRun = discoverySummary;

            setCurrent("fetching_import_weather", "import", reason, userId, startTime, endTime);
            WeatherRunSummary fetchSummary = fetchWithSummary("import", reason, "IMPORT_BACKFILL", userId, startTime, endTime);
            return combinedSummary(discoverySummary, fetchSummary, "IMPORT_BACKFILL");
        } catch (RuntimeException e) {
            log.error("Weather import enrichment failed for user {} from {} to {}: {}",
                    userId, startTime, endTime, e.getMessage(), e);
            return errorSummary("import", reason, "IMPORT_BACKFILL", e.getMessage());
        } finally {
            clearCurrentAndUnlock();
        }
    }

    public WeatherRunSummary processHistorical(String reason, boolean fetchAfterDiscovery, int maxChunks) {
        lockInterruptibly("historical_reconciliation", reason, null, null, null);
        try {
            setCurrent("discovering_historical_targets", "scheduled", reason, null, null, null);
            Instant startedAt = Instant.now();
            long startedNanos = System.nanoTime();
            long resetFailedTargets = weatherService.resetStaleFailedTargetsForRetry();
            WeatherBackfillRunResult result = weatherService.processPendingHistoricalBackfillChunks(maxChunks);
            WeatherRunSummary discoverySummary = WeatherRunSummary.builder()
                    .trigger("scheduled")
                    .reason(reason)
                    .source("HISTORICAL_BACKFILL")
                    .result("success")
                    .startedAt(startedAt)
                    .completedAt(Instant.now())
                    .durationMs(elapsedMillis(startedNanos))
                    .chunksProcessed(result.chunksProcessed())
                    .targetsCreated(result.targetsCreated())
                    .targetsAlreadyKnown(result.targetsAlreadyKnown())
                    .targetsSkipped(result.targetsSkipped())
                    .pendingUserRanges(result.pendingUserRanges())
                    .message("Historical weather reconciliation completed")
                    .build();
            lastDiscoveryRun = discoverySummary;

            int fetchedTargets = 0;
            if (fetchAfterDiscovery && (result.targetsCreated() > 0 || resetFailedTargets > 0)) {
                setCurrent("fetching_discovered_weather", "scheduled", reason, null, null, null);
                WeatherRunSummary fetchSummary = fetchWithSummary("scheduled", reason, "HISTORICAL_BACKFILL", null, null, null);
                fetchedTargets = fetchSummary.getFetchedTargets();
            }
            discoverySummary.setFetchedTargets(fetchedTargets);
            return discoverySummary;
        } finally {
            clearCurrentAndUnlock();
        }
    }

    public WeatherRunSummary processOngoingDiscovery(String reason) {
        lockInterruptibly("ongoing_discovery", reason, null, null, null);
        try {
            setCurrent("discovering_ongoing_targets", "scheduled", reason, null, null, null);
            Instant startedAt = Instant.now();
            long startedNanos = System.nanoTime();
            WeatherTargetQueueResponse response = weatherService.discoverOngoingTargets();
            WeatherRunSummary summary = discoverySummary("scheduled", reason, "ONGOING", startedAt, startedNanos, response, 0, null);
            lastDiscoveryRun = summary;
            return summary;
        } finally {
            clearCurrentAndUnlock();
        }
    }

    public WeatherRunSummary fetchScheduled(String reason) {
        lockInterruptibly("sample_fetch", reason, null, null, null);
        try {
            setCurrent("fetching_weather", "scheduled", reason, null, null, null);
            return fetchWithSummary("scheduled", reason, "ANY", null, null, null);
        } finally {
            clearCurrentAndUnlock();
        }
    }

    public WeatherRunSummary probeProviderHealth(String reason) {
        lockInterruptibly("health_probe", reason, null, null, null);
        try {
            setCurrent("probing_provider_health", "scheduled", reason, null, null, null);
            Instant startedAt = Instant.now();
            long startedNanos = System.nanoTime();
            boolean restored = weatherService.probeProviderHealth();
            return WeatherRunSummary.builder()
                    .trigger("scheduled")
                    .reason(reason)
                    .source("PROVIDER_HEALTH")
                    .result(restored ? "restored" : "success")
                    .message(restored ? "Provider health restored" : "Provider health probe completed")
                    .startedAt(startedAt)
                    .completedAt(Instant.now())
                    .durationMs(elapsedMillis(startedNanos))
                    .build();
        } finally {
            clearCurrentAndUnlock();
        }
    }

    public WeatherRunSummary processNow() {
        if (!processingLock.tryLock()) {
            return WeatherRunSummary.builder()
                    .trigger("admin")
                    .reason("manual weather processing")
                    .source("ANY")
                    .result("already_running")
                    .message("Weather processing is already running")
                    .startedAt(Instant.now())
                    .completedAt(Instant.now())
                    .build();
        }
        try {
            setCurrent("processing_admin_request", "admin", "manual weather processing", null, null, null);
            WeatherRunSummary discovery = processHistoricalWithoutLock("manual weather processing", false);
            setCurrent("fetching_weather", "admin", "manual weather processing", null, null, null);
            WeatherRunSummary fetch = fetchWithSummary("admin", "manual weather processing", "ANY", null, null, null);
            return combinedSummary(discovery, fetch, "ANY");
        } finally {
            clearCurrentAndUnlock();
        }
    }

    private WeatherRunSummary processHistoricalWithoutLock(String reason, boolean fetchAfterDiscovery) {
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();
        long resetFailedTargets = weatherService.resetStaleFailedTargetsForRetry();
        WeatherBackfillRunResult result = weatherService.processPendingHistoricalBackfillChunks(
                configurationService.backfillDiscoveryChunksPerRun());
        WeatherRunSummary summary = WeatherRunSummary.builder()
                .trigger("admin")
                .reason(reason)
                .source("HISTORICAL_BACKFILL")
                .result("success")
                .message("Historical weather reconciliation completed")
                .startedAt(startedAt)
                .completedAt(Instant.now())
                .durationMs(elapsedMillis(startedNanos))
                .chunksProcessed(result.chunksProcessed())
                .targetsCreated(result.targetsCreated())
                .targetsAlreadyKnown(result.targetsAlreadyKnown())
                .targetsSkipped(result.targetsSkipped())
                .pendingUserRanges(result.pendingUserRanges())
                .build();
        lastDiscoveryRun = summary;
        if (fetchAfterDiscovery && (result.targetsCreated() > 0 || resetFailedTargets > 0)) {
            WeatherRunSummary fetchSummary = fetchWithSummary("admin", reason, "HISTORICAL_BACKFILL", null, null, null);
            summary.setFetchedTargets(fetchSummary.getFetchedTargets());
        }
        return summary;
    }

    private WeatherRunSummary fetchWithSummary(String trigger, String reason, String source,
                                               UUID userId, Instant rangeStart, Instant rangeEnd) {
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();
        int fetched = weatherService.fetchQueuedSamples();
        String blockedReason = weatherService.status().getFetchBlockedReason();
        WeatherRunSummary summary = WeatherRunSummary.builder()
                .trigger(trigger)
                .reason(reason)
                .source(source)
                .result(blockedReason == null ? "success" : "deferred")
                .message(blockedReason == null ? "Weather fetch completed" : blockedReason)
                .startedAt(startedAt)
                .completedAt(Instant.now())
                .durationMs(elapsedMillis(startedNanos))
                .fetchedTargets(fetched)
                .build();
        lastFetchRun = summary;
        if (userId != null) {
            setCurrent("weather_fetch_completed", trigger, reason, userId, rangeStart, rangeEnd);
        }
        return summary;
    }

    private WeatherRunSummary discoverySummary(String trigger, String reason, String source,
                                               Instant startedAt, long startedNanos,
                                               WeatherTargetQueueResponse response,
                                               long pendingUserRanges,
                                               String message) {
        return WeatherRunSummary.builder()
                .trigger(trigger)
                .reason(reason)
                .source(source)
                .result("success")
                .message(message == null ? "Weather target discovery completed" : message)
                .startedAt(startedAt)
                .completedAt(Instant.now())
                .durationMs(elapsedMillis(startedNanos))
                .targetsCreated(response.getTargetsCreated())
                .targetsAlreadyKnown(response.getTargetsAlreadyKnown())
                .targetsSkipped(response.getTargetsSkipped())
                .pendingUserRanges(pendingUserRanges)
                .build();
    }

    private WeatherRunSummary combinedSummary(WeatherRunSummary discovery, WeatherRunSummary fetch, String source) {
        return WeatherRunSummary.builder()
                .trigger(discovery.getTrigger())
                .reason(discovery.getReason())
                .source(source)
                .result(fetch.getResult())
                .message(fetch.getMessage())
                .startedAt(discovery.getStartedAt())
                .completedAt(fetch.getCompletedAt())
                .durationMs(Duration.between(discovery.getStartedAt(), fetch.getCompletedAt()).toMillis())
                .chunksProcessed(discovery.getChunksProcessed())
                .targetsCreated(discovery.getTargetsCreated())
                .targetsAlreadyKnown(discovery.getTargetsAlreadyKnown())
                .targetsSkipped(discovery.getTargetsSkipped())
                .fetchedTargets(fetch.getFetchedTargets())
                .pendingUserRanges(discovery.getPendingUserRanges())
                .build();
    }

    private WeatherRunSummary skippedSummary(String trigger, String reason, String source, String message) {
        Instant now = Instant.now();
        return WeatherRunSummary.builder()
                .trigger(trigger)
                .reason(reason)
                .source(source)
                .result("skipped")
                .message(message)
                .startedAt(now)
                .completedAt(now)
                .build();
    }

    private WeatherRunSummary errorSummary(String trigger, String reason, String source, String message) {
        Instant now = Instant.now();
        return WeatherRunSummary.builder()
                .trigger(trigger)
                .reason(reason)
                .source(source)
                .result("error")
                .message(message)
                .startedAt(now)
                .completedAt(now)
                .build();
    }

    private void lockInterruptibly(String trigger, String reason, UUID userId, Instant rangeStart, Instant rangeEnd) {
        if (processingLock.tryLock()) {
            return;
        }
        waitingWorkers.incrementAndGet();
        WeatherProcessingStatus active = currentProcessing;
        if (active != null) {
            log.info("Weather {} waiting for current weather processing: activeTrigger={}, activePhase={}, reason={}",
                    trigger, active.getTrigger(), active.getPhase(), reason);
        }
        try {
            processingLock.lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for weather processing lock", e);
        } finally {
            waitingWorkers.decrementAndGet();
        }
    }

    private void setCurrent(String phase, String trigger, String reason, UUID userId, Instant rangeStart, Instant rangeEnd) {
        currentProcessing = WeatherProcessingStatus.builder()
                .running(true)
                .waitingWorkers(waitingWorkers.get())
                .phase(phase)
                .trigger(trigger)
                .reason(reason)
                .userId(userId)
                .rangeStart(rangeStart)
                .rangeEnd(rangeEnd)
                .startedAt(Instant.now())
                .build();
    }

    private WeatherProcessingStatus snapshotProcessing() {
        WeatherProcessingStatus active = currentProcessing;
        if (active == null) {
            return WeatherProcessingStatus.builder()
                    .running(false)
                    .waitingWorkers(waitingWorkers.get())
                    .build();
        }
        active.setWaitingWorkers(waitingWorkers.get());
        return active;
    }

    private void clearCurrentAndUnlock() {
        currentProcessing = null;
        processingLock.unlock();
    }

    private long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }
}
