package org.github.tess1o.geopulse.coverage.jobs;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.Identifier;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.admin.service.BackupMaintenanceService;
import org.github.tess1o.geopulse.coverage.CoverageDefaults;
import org.github.tess1o.geopulse.coverage.repository.CoverageRepository;
import org.github.tess1o.geopulse.coverage.service.CoverageProcessingService;
import org.github.tess1o.geopulse.importdata.service.ImportJobService;
import org.github.tess1o.geopulse.prometheus.GeoPulseWorkloadMetrics;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

@ApplicationScoped
@Slf4j
public class CoverageCalculationJob {

    private final CoverageRepository coverageRepository;
    private final CoverageProcessingService processingService;
    private final ImportJobService importJobService;

    @Inject
    @Identifier("coverage-processing")
    ExecutorService executorService;

    @Inject
    GeoPulseWorkloadMetrics workloadMetrics;

    @Inject
    BackupMaintenanceService backupMaintenanceService;

    @ConfigProperty(name = "geopulse.coverage.processing.max-concurrent-tasks", defaultValue = "2")
    @StaticInitSafe
    int maxConcurrentTasks;

    @ConfigProperty(name = "geopulse.coverage.processing.stale-timeout-seconds",
            defaultValue = "" + CoverageDefaults.PROCESSING_STALE_TIMEOUT_SECONDS)
    @StaticInitSafe
    int processingStaleTimeoutSeconds;

    private Semaphore semaphore;

    @Inject
    public CoverageCalculationJob(CoverageRepository coverageRepository,
                                  CoverageProcessingService processingService,
                                  ImportJobService importJobService) {
        this.coverageRepository = coverageRepository;
        this.processingService = processingService;
        this.importJobService = importJobService;
    }

    @PostConstruct
    void init() {
        semaphore = new Semaphore(maxConcurrentTasks);
        log.info("Initialized CoverageCalculationJob with max {} concurrent tasks", maxConcurrentTasks);
    }

    @PreDestroy
    void shutdown() {
        if (executorService != null) {
            executorService.shutdown();
            log.info("Shutdown CoverageCalculationJob executor service");
        }
    }

    @Scheduled(every = "${geopulse.coverage.job.interval:2h}", delayed = "${geopulse.coverage.job.delay:0m}")
    @Blocking
    public void processCoverage() {
        if (backupMaintenanceService != null && backupMaintenanceService.isRestoreBlocked()) {
            log.info("Skipping coverage processing while full backup restore is running");
            return;
        }
        long startedAtNanos = metricsStart();
        String result = "success";
        List<UUID> usersToProcess = coverageRepository.findUsersWithNewCoverage(
                CoverageDefaults.MAX_ACCURACY_METERS,
                processingStaleTimeoutSeconds
        );
        countUsers("discovered", usersToProcess.size());

        if (usersToProcess.isEmpty()) {
            recordScheduler(startedAtNanos, "empty");
            return;
        }

        List<UUID> eligibleUsers = usersToProcess.stream()
                .filter(userId -> {
                    boolean hasActiveImport = importJobService.hasActiveImportJob(userId);
                    if (hasActiveImport) {
                        log.info("Skipping scheduled coverage update for user {} because an import is active", userId);
                        countUsers("skipped_import", 1);
                    }
                    return !hasActiveImport;
                })
                .toList();
        countUsers("eligible", eligibleUsers.size());

        if (eligibleUsers.isEmpty()) {
            recordScheduler(startedAtNanos, "skipped");
            return;
        }

        log.info("Starting coverage update for {} users", eligibleUsers.size());

        for (UUID userId : eligibleUsers) {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                result = "interrupted";
                log.warn("Interrupted while waiting to schedule coverage processing for user {}", userId);
                break;
            }

            try {
                countUsers("submitted", 1);
                CompletableFuture.runAsync(() -> {
                    try {
                        log.info("Updating coverage for user {}", userId);
                        processingService.processUserCoverage(userId);
                        log.info("Finished updating coverage for user {}", userId);
                    } catch (Exception e) {
                        log.error("Failed to update coverage for user {}: {}", userId, e.getMessage(), e);
                    } finally {
                        semaphore.release();
                    }
                }, executorService)
                        .exceptionally(throwable -> {
                            log.error("Failed to process coverage for user {}: {}", userId, throwable.getMessage(), throwable);
                            return null;
                        });
            } catch (RejectedExecutionException e) {
                semaphore.release();
                result = "rejected";
                countUsers("rejected", 1);
                log.error("Failed to submit coverage task for user {}: {}", userId, e.getMessage(), e);
            }
        }
        recordScheduler(startedAtNanos, result);
    }

    private long metricsStart() {
        return workloadMetrics == null ? System.nanoTime() : workloadMetrics.start();
    }

    private void recordScheduler(long startedAtNanos, String result) {
        if (workloadMetrics == null) {
            return;
        }
        workloadMetrics.recordTimer("geopulse.coverage.job.duration", startedAtNanos,
                "component", "coverage",
                "trigger", "scheduled",
                "mode", "incremental",
                "result", result);
    }

    private void countUsers(String result, long count) {
        if (workloadMetrics == null || count <= 0) {
            return;
        }
        workloadMetrics.increment("geopulse.coverage.users_scheduled", count,
                "component", "coverage",
                "trigger", "scheduled",
                "result", result);
    }
}
