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
import org.github.tess1o.geopulse.coverage.CoverageDefaults;
import org.github.tess1o.geopulse.coverage.repository.CoverageRepository;
import org.github.tess1o.geopulse.coverage.service.CoverageProcessingService;

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

    @Inject
    @Identifier("coverage-processing")
    ExecutorService executorService;

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
                                  CoverageProcessingService processingService) {
        this.coverageRepository = coverageRepository;
        this.processingService = processingService;
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
        List<UUID> usersToProcess = coverageRepository.findUsersWithNewCoverage(
                CoverageDefaults.MAX_ACCURACY_METERS,
                processingStaleTimeoutSeconds
        );

        if (usersToProcess.isEmpty()) {
            return;
        }

        log.info("Starting coverage update for {} users", usersToProcess.size());

        for (UUID userId : usersToProcess) {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting to schedule coverage processing for user {}", userId);
                break;
            }

            try {
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
                log.error("Failed to submit coverage task for user {}: {}", userId, e.getMessage(), e);
            }
        }
    }
}
