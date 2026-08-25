package org.github.tess1o.geopulse.mapmatching.service;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.mapmatching.dto.MapMatchingAdminStatusDTO;
import org.github.tess1o.geopulse.mapmatching.event.MapMatchingSettingsChangedEvent;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingReconciliation;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingSource;
import org.github.tess1o.geopulse.mapmatching.model.TimelineTripPathMatchEntity;
import org.github.tess1o.geopulse.mapmatching.repository.MapMatchingReconciliationRepository;
import org.github.tess1o.geopulse.mapmatching.repository.TimelineTripPathMatchRepository;
import org.github.tess1o.geopulse.prometheus.GeoPulseWorkloadMetrics;
import org.github.tess1o.geopulse.streaming.events.TimelineDataChangedEvent;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@ApplicationScoped
public class MapMatchingWorker {
    private final MapMatchingConfiguration configuration;
    private final TimelineTripPathMatchRepository targetRepository;
    private final MapMatchingReconciliationRepository reconciliationRepository;
    private final TimelineTripRepository tripRepository;
    private final MapMatchingService mapMatchingService;

    @Inject
    @Identifier("map-matching-processing")
    ExecutorService executor;

    @Inject
    GeoPulseWorkloadMetrics workloadMetrics;

    @Inject
    MapMatchingWorkerExecution workerExecution;

    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean rerunRequested = new AtomicBoolean();
    private volatile String phase = "IDLE";
    private volatile String trigger;
    private volatile String lastError;
    private volatile Instant startedAt;
    private volatile Instant lastCompletedAt;

    public MapMatchingWorker(MapMatchingConfiguration configuration,
                             TimelineTripPathMatchRepository targetRepository,
                             MapMatchingReconciliationRepository reconciliationRepository,
                             TimelineTripRepository tripRepository,
                             MapMatchingService mapMatchingService) {
        this.configuration = configuration;
        this.targetRepository = targetRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.tripRepository = tripRepository;
        this.mapMatchingService = mapMatchingService;
    }

    void onStartup(@Observes StartupEvent ignored) {
        if (configuration.backfillEnabled()) {
            reconciliationRepository.enqueueAllTripOwners(MapMatchingSource.HISTORICAL, Instant.now());
        }
        wake("startup");
    }

    void onTimelineChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) TimelineDataChangedEvent event) {
        submitTimelineChanged(event.getUserId(), event.getAffectedFrom(), event.getAffectedTo());
    }

    private void submitTimelineChanged(UUID userId, Instant affectedFrom, Instant affectedTo) {
        try {
            executor.submit(() -> workerExecution.run(() -> {
                try {
                    handleTimelineChanged(userId, affectedFrom, affectedTo);
                } catch (RuntimeException e) {
                    phase = "BLOCKED";
                    lastError = "Map-matching timeline change handler failed: " + e.getMessage();
                    log.error(lastError, e);
                }
            }));
        } catch (RuntimeException e) {
            phase = "BLOCKED";
            lastError = "Unable to submit map-matching timeline change handler: " + e.getMessage();
            log.error(lastError, e);
        }
    }

    private void handleTimelineChanged(UUID userId, Instant affectedFrom, Instant affectedTo) {
        if (!configuration.isEnabled() || !configuration.automaticEnabled()) return;
        Instant eligibleAt = Instant.now().plus(configuration.quietPeriodMinutes(), ChronoUnit.MINUTES);
        reconciliationRepository.enqueue(userId, affectedFrom, affectedTo, MapMatchingSource.AUTOMATIC, eligibleAt);
        log.info("Queued automatic map matching reconciliation for user {} from {} to {}, eligibleAt={}",
                userId, affectedFrom, affectedTo, eligibleAt);
        wake("timeline changed");
    }

    void onSettingsChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) MapMatchingSettingsChangedEvent event) {
        submitSettingsChanged(event.key());
    }

    private void submitSettingsChanged(String key) {
        try {
            executor.submit(() -> workerExecution.run(() -> {
                try {
                    handleSettingsChanged(key);
                } catch (RuntimeException e) {
                    phase = "BLOCKED";
                    lastError = "Map-matching settings change handler failed: " + e.getMessage();
                    log.error(lastError, e);
                }
            }));
        } catch (RuntimeException e) {
            phase = "BLOCKED";
            lastError = "Unable to submit map-matching settings change handler: " + e.getMessage();
            log.error(lastError, e);
        }
    }

    private void handleSettingsChanged(String key) {
        if (configuration.isEnabled() && configuration.backfillEnabled()) {
            if (affectsCache(key)) {
                reconciliationRepository.restartAllTripOwners(MapMatchingSource.HISTORICAL, Instant.now());
            } else if ("map-matching.backfill.enabled".equals(key)) {
                reconciliationRepository.enqueueAllTripOwners(MapMatchingSource.HISTORICAL, Instant.now());
            }
        }
        wake("setting changed: " + key);
    }

    @Scheduled(every = "${geopulse.timeline.map-matching.worker.interval:15s}", delayed = "20s",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void watchdog() {
        wake("watchdog");
    }

    @Scheduled(cron = "${geopulse.timeline.map-matching.cache.cleanup.cron:0 45 3 * * ?}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void cleanupDetachedCache() {
        Instant now = Instant.now();
        long deleted = targetRepository.cleanupDetached(
                now.minus(30, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.HOURS));
        if (deleted > 0) {
            log.info("Map-matching detached cache cleanup removed {} rows", deleted);
        }
    }

    public boolean wake(String reason) {
        rerunRequested.set(true);
        boolean submitted = running.compareAndSet(false, true);
        if (submitted) submitDrain(reason == null ? "manual" : reason);
        return submitted;
    }

    private void submitDrain(String reason) {
        try {
            executor.submit(() -> workerExecution.run(() -> drain(reason)));
        } catch (RuntimeException e) {
            running.set(false);
            phase = "BLOCKED";
            lastError = "Unable to submit map-matching worker: " + e.getMessage();
            log.error(lastError, e);
        }
    }

    private void drain(String reason) {
        long metricStarted = workloadMetrics == null ? System.nanoTime() : workloadMetrics.start();
        startedAt = Instant.now();
        trigger = reason;
        lastError = null;
        int processed = 0;
        Map<UUID, Double> maxAccuracyByUser = new HashMap<>();
        log.info("Map-matching pipeline started: trigger={}", reason);
        try {
            if (!configuration.isEnabled() || !"valhalla".equals(configuration.provider())
                    || !configuration.valhallaConfigured()) {
                phase = "IDLE";
                return;
            }
            mapMatchingService.resetStaleProcessing();
            while (true) {
                rerunRequested.set(false);
                phase = "DISCOVERING";
                int discovered = discoverNextChunk(maxAccuracyByUser);
                phase = "MATCHING";
                List<TimelineTripPathMatchEntity> targets = targetRepository.claimPending(
                        configuration.getWorkerBatchSize(), configuration.automaticEnabled(), configuration.backfillEnabled());
                processed += mapMatchingService.processTargets(targets, maxAccuracyByUser);
                if (targets.isEmpty() && discovered == 0 && !rerunRequested.get()) break;
            }
            phase = "IDLE";
        } catch (Exception e) {
            phase = "BLOCKED";
            lastError = e.getMessage();
            log.error("Map-matching pipeline failed: trigger={}, error={}", reason, e.getMessage(), e);
        } finally {
            lastCompletedAt = Instant.now();
            try {
                recordMetrics(metricStarted, phase);
                log.info("Map-matching pipeline completed: trigger={}, phase={}, processedTargets={}, pendingReconciliations={}, error={}",
                        reason, phase, processed, reconciliationRepository.countPending(), lastError);
            } catch (RuntimeException e) {
                log.warn("Unable to record final map-matching worker status: {}", e.getMessage());
            } finally {
                running.set(false);
                if (rerunRequested.getAndSet(false) && running.compareAndSet(false, true)) {
                    submitDrain("coalesced wake");
                }
            }
        }
    }

    private int discoverNextChunk(Map<UUID, Double> maxAccuracyByUser) {
        var claimed = reconciliationRepository.claimNext(
                configuration.automaticEnabled(), configuration.backfillEnabled());
        if (claimed.isEmpty()) return 0;
        MapMatchingReconciliation reconciliation = claimed.get();
        try {
            int limit = Math.max(1, configuration.getWorkerBatchSize());
            List<TimelineTripEntity> trips = tripRepository.findMapMatchingChunk(
                    reconciliation.userId(), reconciliation.cursorAt(), reconciliation.cursorTripId(),
                    reconciliation.rangeEnd(), limit);
            if (trips.isEmpty()) {
                reconciliationRepository.advance(
                        reconciliation, reconciliation.rangeEnd(), Long.MAX_VALUE, 0, true);
                return 0;
            }
            mapMatchingService.enqueueBackgroundTrips(
                    reconciliation.userId(), trips, reconciliation.source(), maxAccuracyByUser);
            TimelineTripEntity lastTrip = trips.getLast();
            Instant nextCursor = lastTrip.getTimestamp();
            boolean complete = trips.size() < limit || nextCursor.isAfter(reconciliation.rangeEnd());
            reconciliationRepository.advance(
                    reconciliation, nextCursor, lastTrip.getId(), trips.size(), complete);
            return trips.size();
        } catch (RuntimeException e) {
            reconciliationRepository.release(reconciliation.id());
            throw e;
        }
    }

    public MapMatchingAdminStatusDTO status() {
        Map<String, Long> targetsByStatus = targetRepository.countByStatus();
        Map<String, Long> targetsBySource = targetRepository.countBySource();
        var backfillProgress = reconciliationRepository.historicalProgress();
        Instant lastActivityAt = latest(
                targetRepository.lastUpdatedAt(),
                reconciliationRepository.lastUpdatedAt());
        return MapMatchingAdminStatusDTO.builder()
                .enabled(configuration.isEnabled()).configured(configuration.valhallaConfigured())
                .worker(MapMatchingAdminStatusDTO.Worker.builder()
                        .running(running.get()).phase(phase).trigger(trigger)
                        .startedAt(startedAt).lastActivityAt(lastActivityAt).lastError(lastError)
                        .build())
                .backfill(MapMatchingAdminStatusDTO.Backfill.builder()
                        .enabled(configuration.backfillEnabled())
                        .totalTrips(backfillProgress.totalTrips())
                        .scannedTrips(backfillProgress.scannedTrips())
                        .remainingTrips(backfillProgress.remainingTrips())
                        .percent(backfillProgress.percent())
                        .totalUsers(backfillProgress.totalUsers())
                        .completedUsers(backfillProgress.completedUsers())
                        .remainingUsers(backfillProgress.remainingUsers())
                        .build())
                .queue(MapMatchingAdminStatusDTO.Queue.builder()
                        .queued(targetsByStatus.getOrDefault("PENDING", 0L))
                        .processing(targetsByStatus.getOrDefault("PROCESSING", 0L))
                        .oldestQueuedAt(targetRepository.oldestQueuedAt())
                        .build())
                .diagnostics(MapMatchingAdminStatusDTO.Diagnostics.builder()
                        .lastWorkerCycleCompletedAt(lastCompletedAt)
                        .oldestReconciliationCursorAt(reconciliationRepository.oldestCursor())
                        .pendingReconciliations(reconciliationRepository.countPending())
                        .targetsByStatus(targetsByStatus)
                        .targetsBySource(targetsBySource)
                        .build())
                .build();
    }

    private Instant latest(Instant first, Instant second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.isAfter(second) ? first : second;
    }

    private boolean affectsCache(String key) {
        return key != null && (key.equals("map-matching.provider")
                || key.equals("map-matching.valhalla.base-url")
                || key.equals("map-matching.max-input-points")
                || key.equals("map-matching.max-trip-duration-hours")
                || key.equals("map-matching.quality.min-raw-distance-meters")
                || key.equals("map-matching.quality.min-distance-coverage-percent")
                || key.equals("map-matching.quality.max-discontinuity-percent")
                || key.equals("map-matching.quality.max-short-discontinuity-meters"));
    }

    private void recordMetrics(long started, String result) {
        if (workloadMetrics == null) return;
        workloadMetrics.recordTimer("geopulse.map_matching.worker.duration", started,
                "component", "map_matching", "result", result == null ? "unknown" : result.toLowerCase());
        targetRepository.countByStatus().forEach((status, count) ->
                workloadMetrics.setGauge("geopulse.map_matching.queue.depth", count,
                        "queue", "targets", "status", status));
        workloadMetrics.setGauge("geopulse.map_matching.queue.depth", reconciliationRepository.countPending(),
                "queue", "reconciliations", "status", "PENDING");
    }
}
