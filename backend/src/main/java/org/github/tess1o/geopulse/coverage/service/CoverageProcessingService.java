package org.github.tess1o.geopulse.coverage.service;

import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.coverage.CoverageDefaults;
import org.github.tess1o.geopulse.coverage.repository.CoverageRepository;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@ApplicationScoped
@Slf4j
public class CoverageProcessingService {

    private final CoverageRepository coverageRepository;
    private final CoverageService coverageService;
    private final ExecutorService executorService;

    @ConfigProperty(name = "geopulse.coverage.processing.stale-timeout-seconds",
            defaultValue = "" + CoverageDefaults.PROCESSING_STALE_TIMEOUT_SECONDS)
    int processingStaleTimeoutSeconds;

    @Inject
    public CoverageProcessingService(CoverageRepository coverageRepository,
                                     CoverageService coverageService,
                                     @Identifier("coverage-processing") ExecutorService executorService) {
        this.coverageRepository = coverageRepository;
        this.coverageService = coverageService;
        this.executorService = executorService;
    }

    public boolean startProcessingAsync(UUID userId) {
        if (!coverageRepository.tryStartProcessing(userId, processingStaleTimeoutSeconds)) {
            return false;
        }

        try {
            CompletableFuture.runAsync(() -> runProcessing(userId), executorService)
                    .exceptionally(throwable -> {
                        log.error("Failed to process coverage for user {}: {}", userId, throwable.getMessage(), throwable);
                        return null;
                    });
        } catch (RuntimeException e) {
            coverageRepository.finishProcessing(userId);
            log.error("Failed to submit coverage processing task for user {}: {}", userId, e.getMessage(), e);
            return false;
        }

        return true;
    }

    public void processUserCoverage(UUID userId) {
        if (!coverageRepository.tryStartProcessing(userId, processingStaleTimeoutSeconds)) {
            return;
        }
        runProcessing(userId);
    }

    private void runProcessing(UUID userId) {
        try {
            coverageService.processUserCoverage(userId);
        } catch (Exception e) {
            log.error("Failed to process coverage for user {}: {}", userId, e.getMessage(), e);
        } finally {
            coverageRepository.finishProcessing(userId);
        }
    }
}
