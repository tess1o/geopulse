package org.github.tess1o.geopulse.coverage.service;

import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
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

    @Inject
    public CoverageProcessingService(CoverageRepository coverageRepository,
                                     CoverageService coverageService,
                                     @Identifier("coverage-processing") ExecutorService executorService) {
        this.coverageRepository = coverageRepository;
        this.coverageService = coverageService;
        this.executorService = executorService;
    }

    public boolean startProcessingAsync(UUID userId) {
        if (!coverageRepository.tryStartProcessing(userId)) {
            return false;
        }

        CompletableFuture.runAsync(() -> runProcessing(userId), executorService)
                .exceptionally(throwable -> {
                    log.error("Failed to process coverage for user {}: {}", userId, throwable.getMessage(), throwable);
                    return null;
                });

        return true;
    }

    public void processUserCoverage(UUID userId) {
        if (!coverageRepository.tryStartProcessing(userId)) {
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
